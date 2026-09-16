import argparse
import csv
import itertools
import json
import os
import queue
import shlex
import signal
import subprocess
import sys
import threading
import time
from datetime import datetime

POSIX = os.name == "posix"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RESULTS_ROOT = os.path.join(SCRIPT_DIR, "matsim_results")

# Prefixes declared via allowPrefixes() in org.eqasim.bavaria.RunSimulation
MODE_CHOICE_PREFIX = "mode-choice-parameter"
COST_PREFIX = "cost-parameter"

active_procs = {}
active_procs_lock = threading.Lock()
shutdown_requested = threading.Event()
print_lock = threading.Lock()


def log(msg):
    with print_lock:
        print(f"[{datetime.now().isoformat()}] {msg}", flush=True)


# --------------------------------------------------
# Signal handler for Slurm cancellations
# --------------------------------------------------

def handle_shutdown(signum, frame):

    log(f"Received signal {signum}, shutting down all runs")
    shutdown_requested.set()

    with active_procs_lock:
        procs = list(active_procs.items())

    for name, proc in procs:
        if proc.poll() is None:
            log(f"Stopping MATSim for run {name}")
            kill_process_tree(proc)

    sys.exit(1)


signal.signal(signal.SIGTERM, handle_shutdown)
signal.signal(signal.SIGINT, handle_shutdown)


# --------------------------------------------------
# Logging helpers
# --------------------------------------------------

def stream_output(pipe, logfile, prefix, mirror_console):

    for line in iter(pipe.readline, ""):

        logfile.write(line)
        logfile.flush()

        if mirror_console:
            with print_lock:
                print(f"{prefix}{line}", end="")

    pipe.close()


def tail(path, n=30):

    try:
        with open(path) as f:
            return "".join(f.readlines()[-n:])

    except Exception:
        return "Unable to read log"


# --------------------------------------------------
# Process utilities
# --------------------------------------------------

def kill_process_tree(proc):

    try:
        if POSIX:
            os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        else:
            proc.terminate()

    except Exception:
        pass


def find_matsim_iteration_dirs(iters_dir):

    dirs = []
    if not os.path.isdir(iters_dir):
        return dirs

    for entry in os.listdir(iters_dir):
        entry_path = os.path.join(iters_dir, entry)
        if os.path.isdir(entry_path) and entry.startswith("it."):
            try:
                n = int(entry[3:])
                dirs.append((n, entry_path))
            except ValueError:
                pass

    dirs.sort(key=lambda item: item[0])
    return dirs


def cleanup_old_matsim_gz_files(matsim_output_dir, keep_last_iteration_with_gz=2):

    iters_dir = os.path.join(matsim_output_dir, "ITERS")
    iteration_dirs = find_matsim_iteration_dirs(iters_dir)
    if not iteration_dirs:
        return 0

    iterations_with_gz = []
    for iteration, path in iteration_dirs:
        for root, _, files in os.walk(path):
            if any(file_name.endswith(".gz") for file_name in files):
                iterations_with_gz.append((iteration, path))
                break

    if not iterations_with_gz:
        return 0

    iterations_with_gz.sort(key=lambda item: item[0])
    keep_iters = {iteration for iteration, _ in iterations_with_gz[-keep_last_iteration_with_gz:]}

    deleted = 0
    for iteration, path in iteration_dirs:
        if iteration in keep_iters:
            continue

        for root, _, files in os.walk(path):
            for file_name in files:
                if file_name.endswith(".gz"):
                    file_path = os.path.join(root, file_name)
                    try:
                        os.remove(file_path)
                        deleted += 1
                    except Exception as exc:
                        log(f"Unable to delete {file_path}: {exc}")

    return deleted


def periodic_matsim_gz_cleanup(matsim_output_dir, proc, run_name, interval_s=600):

    while proc.poll() is None and not shutdown_requested.is_set():
        deleted = cleanup_old_matsim_gz_files(matsim_output_dir)
        if deleted:
            log(f"[{run_name}] cleanup removed {deleted} old .gz file(s)")
        time.sleep(interval_s)


# --------------------------------------------------
# Run specification
# --------------------------------------------------

def slugify(value):

    text = str(value).replace(".", "_").replace(" ", "")
    return "".join(c for c in text if c.isalnum() or c in "_-+")


def make_run_name(index, parameters):
    """Compact, filesystem-safe name carrying the varied values, e.g. 03_pt_alpha_u-0.74."""

    if not parameters:
        return f"{index:02d}_base"

    parts = [f"{slugify(key)}{value}" for key, value in parameters.items()]
    name = f"{index:02d}_" + "_".join(parts)

    # Keep directory names workable; the full assignment stays in
    # calibration_parameters.json and calibration_summary.csv.
    if len(name) > 80:
        name = f"{index:02d}_run"

    return name


def expand_grid(grid):
    """Cartesian product over {parameter: [values]} -> list of {parameter: value}."""

    if not grid:
        return [{}]

    keys = list(grid.keys())
    combinations = itertools.product(*[grid[key] for key in keys])

    return [dict(zip(keys, values)) for values in combinations]


def parse_grid_args(grid_args):
    """--grid pt.alpha_u=-0.5,-0.6,-0.74 -> {"pt.alpha_u": [-0.5, -0.6, -0.74]}"""

    grid = {}

    for entry in grid_args:
        if "=" not in entry:
            raise ValueError(f"--grid expects parameter=v1,v2,...  (got: {entry})")

        key, raw_values = entry.split("=", 1)
        values = [value.strip() for value in raw_values.split(",") if value.strip()]

        if not values:
            raise ValueError(f"No values given for --grid {key}")

        grid[key.strip()] = values

    return grid


def load_spec(path):
    """Read a JSON run specification.

    {
      "base":            {"walk.alpha_u": -0.1, "bike.alpha_u": -1.2},
      "base_cost":       {},
      "extra_args":      ["--use-vdf", "true"],
      "grid":            {"pt.alpha_u": [-0.53, -0.74]},
      "runs":            [{"name": "...", "parameters": {...}, "cost_parameters": {...},
                          "extra_args": [...]}]
    }

    "grid" and "runs" may be combined; both are appended to the queue.
    """

    with open(path) as f:
        spec = json.load(f)

    return spec


def build_runs(args):

    spec = load_spec(args.runs) if args.runs else {}

    base = dict(spec.get("base", {}))
    base_cost = dict(spec.get("base_cost", {}))
    base_extra = list(spec.get("extra_args", []))

    if args.extra_arg:
        base_extra.extend(shlex.split(" ".join(args.extra_arg)))

    variations = []

    grid = dict(spec.get("grid", {}))
    grid.update(parse_grid_args(args.grid))

    for parameters in expand_grid(grid):
        variations.append({"parameters": parameters})

    variations.extend(spec.get("runs", []))

    if not variations:
        raise ValueError("No runs defined. Use --grid, or --runs with a 'grid'/'runs' section.")

    runs = []

    for index, variation in enumerate(variations, start=1):
        varied = dict(variation.get("parameters", {}))
        varied_cost = dict(variation.get("cost_parameters", {}))

        mode_params = dict(base)
        mode_params.update(varied)

        cost_params = dict(base_cost)
        cost_params.update(varied_cost)

        extra_args = base_extra + list(variation.get("extra_args", []))

        name = variation.get("name") or make_run_name(index, {**varied, **varied_cost})

        runs.append({
            "name": name,
            "mode_params": mode_params,
            "cost_params": cost_params,
            "varied": {**varied, **varied_cost},
            "extra_args": extra_args,
        })

    names = [run["name"] for run in runs]
    duplicates = {name for name in names if names.count(name) > 1}
    if duplicates:
        raise ValueError(f"Duplicate run names: {sorted(duplicates)}")

    return runs


# --------------------------------------------------
# Single MATSim run
# --------------------------------------------------

def detect_continue_state(matsim_output_dir):
    """Latest iteration with a written plans file, for restarting an interrupted run."""

    iters_dir = os.path.join(matsim_output_dir, "ITERS")
    if not os.path.isdir(iters_dir):
        return None, None

    latest_iter = None
    for entry in os.listdir(iters_dir):
        if entry.startswith("it."):
            try:
                n = int(entry[3:])
            except ValueError:
                continue

            plans_candidate = os.path.join(iters_dir, entry, f"{n}.plans.xml.gz")
            if os.path.isfile(plans_candidate) and (latest_iter is None or n > latest_iter):
                latest_iter = n

    if latest_iter is None:
        return None, None

    return latest_iter, os.path.join(iters_dir, f"it.{latest_iter}", f"{latest_iter}.plans.xml.gz")


def build_java_cmd(run, args, matsim_output_dir):

    java_cmd = []

    if args.srun:
        java_cmd.extend(["srun", "--exclusive", "-N1", "-n1", f"-c{args.matsim_cpus}"])
        java_cmd.extend(shlex.split(args.srun_args))

    java_cmd.extend([
        "java",
        f"-Xms{args.java_xms}",
        f"-Xmx{args.java_xmx}",
        f"-XX:ActiveProcessorCount={args.matsim_cpus}",
    ])

    if args.gc_log:
        java_cmd.append("-Xlog:gc*,os=info,memops=info")

    java_cmd.extend([
        "-Djava.awt.headless=true",
        "-cp",
        args.jar,
        "org.eqasim.bavaria.RunSimulation",
        "--config-path",
        os.path.abspath(args.config_path),
        "--config:global.numberOfThreads",
        str(args.matsim_cpus),
        "--config:controller.outputDirectory",
        matsim_output_dir,
    ])

    first_iteration = args.first_iteration
    plans_file = os.path.abspath(args.plans_file) if args.plans_file else None
    overwrite = args.overwrite_files

    if args.continue_simulation:
        detected_iteration, detected_plans = detect_continue_state(matsim_output_dir)
        if detected_iteration is not None:
            log(f"[{run['name']}] continuing from iteration {detected_iteration}")
            first_iteration = detected_iteration
            plans_file = detected_plans
            overwrite = True
        else:
            log(f"[{run['name']}] no previous plans file found, starting new simulation")

    if first_iteration is not None:
        java_cmd.extend(["--config:controller.firstIteration", str(first_iteration)])

        if plans_file is None:
            plans_file = os.path.join(matsim_output_dir, "ITERS",
                                      f"it.{first_iteration}", f"{first_iteration}.plans.xml.gz")

    if plans_file:
        java_cmd.extend(["--config:plans.inputPlansFile", plans_file])

    if overwrite or first_iteration is not None:
        java_cmd.extend(["--config:controller.overwriteFiles", "overwriteExistingFiles"])

    for key, value in run["mode_params"].items():
        java_cmd.extend([f"--{MODE_CHOICE_PREFIX}:{key}", str(value)])

    for key, value in run["cost_params"].items():
        java_cmd.extend([f"--{COST_PREFIX}:{key}", str(value)])

    java_cmd.extend(run["extra_args"])

    return java_cmd


def scenario_dir_of(args):

    return os.path.join(args.results_root or RESULTS_ROOT, args.scenario_name)


def run_matsim(run, args):

    scenario_dir = scenario_dir_of(args)
    matsim_output_dir = os.path.join(scenario_dir, run["name"])
    os.makedirs(matsim_output_dir, exist_ok=True)

    java_log_f = os.path.join(scenario_dir, f"java_{run['name']}.log")

    with open(os.path.join(matsim_output_dir, "calibration_parameters.json"), "w") as f:
        json.dump({
            "name": run["name"],
            "mode_choice_parameters": run["mode_params"],
            "cost_parameters": run["cost_params"],
            "varied": run["varied"],
            "extra_args": run["extra_args"],
        }, f, indent=2)

    java_cmd = build_java_cmd(run, args, matsim_output_dir)

    log(f"[{run['name']}] starting: {' '.join(java_cmd)}")

    started_at = time.time()

    proc = subprocess.Popen(
        java_cmd,
        cwd=args.eqasim_dir,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        start_new_session=POSIX,
    )

    with active_procs_lock:
        active_procs[run["name"]] = proc

    java_log = open(java_log_f, "w")

    stream_thread = threading.Thread(
        target=stream_output,
        args=(proc.stdout, java_log, f"[{run['name']}] ", args.stream_console),
        daemon=True,
    )
    stream_thread.start()

    if args.cleanup_gz:
        threading.Thread(
            target=periodic_matsim_gz_cleanup,
            args=(matsim_output_dir, proc, run["name"], args.cleanup_interval),
            daemon=True,
        ).start()

    exit_code = proc.wait()
    stream_thread.join(timeout=10)
    java_log.close()

    with active_procs_lock:
        active_procs.pop(run["name"], None)

    duration_s = time.time() - started_at

    if exit_code == 0:
        log(f"[{run['name']}] finished successfully after {duration_s / 60.0:.1f} min")
    else:
        log(f"[{run['name']}] FAILED with exit code {exit_code} after {duration_s / 60.0:.1f} min")
        log(f"[{run['name']}] last log lines:\n{tail(java_log_f)}")

    return {
        "run": run["name"],
        "exit_code": exit_code,
        "duration_min": round(duration_s / 60.0, 1),
        "output_dir": matsim_output_dir,
        "log": java_log_f,
        "varied": run["varied"],
    }


# --------------------------------------------------
# Worker queue
# --------------------------------------------------

def worker(run_queue, args, results, results_lock):

    while not shutdown_requested.is_set():

        try:
            run = run_queue.get_nowait()
        except queue.Empty:
            return

        try:
            result = run_matsim(run, args)
        except Exception as exc:
            log(f"[{run['name']}] crashed in launcher: {exc}")
            result = {
                "run": run["name"],
                "exit_code": -1,
                "duration_min": 0.0,
                "output_dir": "",
                "log": "",
                "varied": run["varied"],
            }
        finally:
            run_queue.task_done()

        with results_lock:
            results.append(result)


def write_summary(results, runs, scenario_dir, suffix=""):

    varied_keys = sorted({key for run in runs for key in run["varied"]})
    summary_path = os.path.join(scenario_dir, f"calibration_summary{suffix}.csv")

    order = {run["name"]: index for index, run in enumerate(runs)}
    results = sorted(results, key=lambda result: order.get(result["run"], 0))

    with open(summary_path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["run", "exit_code", "duration_min", "output_dir"] + varied_keys)

        for result in results:
            writer.writerow([
                result["run"],
                result["exit_code"],
                result["duration_min"],
                result["output_dir"],
            ] + [result["varied"].get(key, "") for key in varied_keys])

    return summary_path


# --------------------------------------------------
# Main
# --------------------------------------------------

def main():

    parser = argparse.ArgumentParser(
        description="Run several MATSim calibration runs in parallel, varying mode-choice parameters.")

    parser.add_argument("--eqasim-dir", required=True)
    parser.add_argument("--config-path", required=True)
    parser.add_argument("--scenario-name", required=True)

    parser.add_argument("--jar", default="bavaria/target/bavaria-2.0.0.jar",
                        help="Classpath entry, relative to --eqasim-dir.")

    # Parameter variation
    parser.add_argument("--runs",
                        help="JSON file with 'base'/'grid'/'runs' sections (see load_spec docstring).")
    parser.add_argument("--grid", action="append", default=[], metavar="PARAM=V1,V2",
                        help="Vary a mode-choice parameter over a list of values. Repeatable; "
                             "several --grid flags form the cartesian product. "
                             "Example: --grid pt.alpha_u=-0.53,-0.74")
    parser.add_argument("--extra-arg", action="append", default=[],
                        help="Extra argument(s) appended to every java command, e.g. --extra-arg '--use-vdf true'.")

    # Run selection (Slurm job arrays: --run-index $SLURM_ARRAY_TASK_ID)
    parser.add_argument("--run-index", type=int,
                        help="Execute only the run with this 0-based index from the expanded run list. "
                             "Use with a Slurm job array so Slurm acts as the queue.")
    parser.add_argument("--list-runs", action="store_true",
                        help="Print the index -> run mapping and exit (indices match --run-index).")
    parser.add_argument("--results-root",
                        help="Directory holding the per-scenario output (default: matsim_results next to this script). "
                             "Point this at scratch/work space on a cluster.")

    # Parallelism and resources (per run)
    parser.add_argument("--max-parallel", type=int, default=1,
                        help="Number of MATSim runs executed concurrently.")
    parser.add_argument("--matsim-cpus", type=int, default=8, help="Threads per run.")
    parser.add_argument("--java-xms", default="20g", help="Initial heap per run.")
    parser.add_argument("--java-xmx", default="40g", help="Maximum heap per run.")

    parser.add_argument("--srun", action="store_true",
                        help="Wrap each run in 'srun --exclusive -N1 -n1 -c<matsim-cpus>' "
                             "to use Slurm as the queue inside one allocation.")
    parser.add_argument("--srun-args", default="", help="Additional srun arguments.")

    parser.add_argument("--gc-log", action="store_true", help="Enable JVM GC logging.")
    parser.add_argument("--stream-console", action="store_true",
                        help="Mirror MATSim output to the console (interleaved when running in parallel). "
                             "Per-run log files are always written.")

    parser.add_argument("--cleanup-gz", action="store_true",
                        help="Periodically delete old MATSim .gz files from previous iteration output directories")
    parser.add_argument("--cleanup-interval", type=int, default=600,
                        help="Seconds between MATSim .gz cleanup scans.")

    parser.add_argument("--overwrite-files", action="store_true")
    parser.add_argument("--first-iteration", type=int)
    parser.add_argument("--plans-file")
    parser.add_argument("--continue-simulation", action="store_true",
                        help="Per run: resume from the latest iteration found in that run's output directory.")

    parser.add_argument("--dry-run", action="store_true",
                        help="Print the java command of every run and exit.")

    args = parser.parse_args()

    runs = build_runs(args)

    if args.list_runs:
        for index, run in enumerate(runs):
            print(f"{index:3d}  {run['name']}  {run['varied']}")
        print(f"\n{len(runs)} run(s) -> Slurm array range 0-{len(runs) - 1}")
        return 0

    summary_suffix = ""

    if args.run_index is not None:
        if not 0 <= args.run_index < len(runs):
            log(f"--run-index {args.run_index} out of range, the spec expands to {len(runs)} run(s) "
                f"(valid: 0-{len(runs) - 1})")
            return 2

        runs = [runs[args.run_index]]
        summary_suffix = f"_{args.run_index:03d}"
        log(f"Selected run {args.run_index}: {runs[0]['name']}")

    scenario_dir = scenario_dir_of(args)

    log(f"Prepared {len(runs)} run(s), {args.max_parallel} in parallel")

    if args.dry_run:
        for run in runs:
            output_dir = os.path.join(scenario_dir, run["name"])
            print(f"\n# {run['name']}  varied: {run['varied']}")
            print(" ".join(build_java_cmd(run, args, output_dir)))
        return 0

    os.makedirs(scenario_dir, exist_ok=True)

    run_queue = queue.Queue()
    for run in runs:
        run_queue.put(run)

    results = []
    results_lock = threading.Lock()

    workers = []
    for _ in range(min(args.max_parallel, len(runs))):
        thread = threading.Thread(target=worker, args=(run_queue, args, results, results_lock))
        thread.start()
        workers.append(thread)

    for thread in workers:
        thread.join()

    summary_path = write_summary(results, runs, scenario_dir, summary_suffix)

    failed = [result for result in results if result["exit_code"] != 0]

    log(f"Finished {len(results)} run(s), {len(failed)} failed. Summary: {summary_path}")

    for result in results:
        log(f"  {result['run']}: exit={result['exit_code']} duration={result['duration_min']}min")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
