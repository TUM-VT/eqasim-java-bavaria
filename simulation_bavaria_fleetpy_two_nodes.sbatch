#!/bin/bash
#
# Run two EQASim + FleetPy runs in parallel: one (sc1_30) on node 0, one (sc_1_100) on node 1.
# Uses a single job with 2 nodes to stay within the "max 2 fat jobs" limit.
#
#SBATCH -J sc_30_and_100
#SBATCH --output=sim_sc_30_and_100_%j.log
#SBATCH --error=sim_sc_30_and_100_%j.log
#SBATCH --nodes=2
#SBATCH --ntasks=2
#SBATCH --ntasks-per-node=1
#SBATCH --cpus-per-task=48
#SBATCH --mem=600GB
#SBATCH --time=48:00:00
#SBATCH --account=pn39mu
#SBATCH --partition=fat
#SBATCH --mail-type=BEGIN,END,FAIL
#SBATCH --mail-user=elena.natterer@tum.de
#SBATCH --get-user-env
#SBATCH --export=none

set -euo pipefail

# SuperMUC-NG: initialize module environment.
module load slurm_setup

export FONTCONFIG_PATH=$HOME/test_java_font_4u/fonts
export FONTCONFIG_FILE=$HOME/test_java_font_4u/fonts/fonts.conf
export FC_FONT_PATH=$HOME/test_java_font_4u/fonts

# FleetPy python
FLEETPY_ENV="/dss/dsshome1/06/ge49wav3/.conda/envs/fleetpy_new"
FLEETPY_PYTHON="${FLEETPY_ENV}/bin/python"
if [ ! -x "${FLEETPY_PYTHON}" ]; then
  echo "ERROR: FleetPy python not found at ${FLEETPY_PYTHON}" >&2
  exit 1
fi

EQASIM_DIR="${SLURM_SUBMIT_DIR}"
FLEETPY_DIR="/hppfs/work/pn39mu/ge49wav3/mount_point_work_dir/MINGA_project/FleetPy"
NETWORK_PATH="//hppfs/work/pn39mu/ge49wav3/mount_point_work_dir/MINGA_project/eqasim-java-bavaria/populations/muenchen_2024_10pct/synthetic_population_output/munich_network.xml.gz"

MATSim_CPUS=44
FLEETPY_CPUS=4
MIN_WAIT_SECONDS=480
WAIT_INTERVAL_SECONDS=5
MAX_WAIT_SECONDS=900

# ---- Scenario sc1_30 (runs on first node) ----
PORT_SC1_30=6651
JAVA_LOG_SC1_30="${EQASIM_DIR}/output_config_sc1_30_drt.log"
FLEETPY_LOG_SC1_30="${EQASIM_DIR}/output_config_sc1_30_drt.log"
CONFIG_PATH_SC1_30="${EQASIM_DIR}/populations/muenchen_2024_10pct/synthetic_population_output/munich_config_with_drt_sbatch_30.xml"
OUTPUT_DIR_SC1_30="${EQASIM_DIR}/simulation_output/config_drt_30"
FLEETPY_CONFIG_SC1_30="${FLEETPY_DIR}/studies/scenarios/constant_config_minga_sc1_10pct_30.yaml"

# ---- Scenario sc_1_100 (runs on second node) ----
PORT_SC1_100=6652
JAVA_LOG_SC1_100="${EQASIM_DIR}/output_config_sc1_drt.log"
FLEETPY_LOG_SC1_100="${EQASIM_DIR}/output_config_sc1_drt.log"
CONFIG_PATH_SC1_100="${EQASIM_DIR}/populations/muenchen_2024_10pct/synthetic_population_output/munich_config_with_drt_sbatch.xml"
OUTPUT_DIR_SC1_100="${EQASIM_DIR}/simulation_output/config_drt_100"
FLEETPY_CONFIG_SC1_100="${FLEETPY_DIR}/studies/scenarios/constant_config_minga_sc1_10pct.yaml"

# Ensure srun-invoked bash on the other node inherits these (for Java/Python).
export FLEETPY_PYTHON EQASIM_DIR FLEETPY_DIR NETWORK_PATH

# Resolve allocated nodes so we can pin each run to one node.
# Prefer scontrol (faster); fallback to srun hostname if scontrol fails or returns wrong count.
NODES=()
if command -v scontrol &>/dev/null && [ -n "${SLURM_NODELIST:-}" ]; then
  NODES=( $(scontrol show hostnames "$SLURM_NODELIST") )
fi
if [ ${#NODES[@]} -ne 2 ]; then
  echo "[$(date -Is)] Fallback: getting nodes via srun hostname" >&2
  NODES=( $(srun --nodes=2 --ntasks=2 hostname) )
fi
if [ ${#NODES[@]} -ne 2 ]; then
  echo "ERROR: Expected exactly 2 nodes, got ${#NODES[@]}. SLURM_NODELIST=${SLURM_NODELIST:-unset}" >&2
  exit 1
fi
NODE0="${NODES[0]}"
NODE1="${NODES[1]}"
echo "[$(date -Is)] Allocated nodes: NODE0=${NODE0} (sc1_30), NODE1=${NODE1} (sc_1_100)" >&2

# Helper: run one full MATSim+FleetPy stack on a given node with given config (used twice below).
run_one_node() {
  local NODE="$1"
  local LABEL="$2"
  local PORT="$3"
  local JAVA_LOG="$4"
  local FLEETPY_LOG="$5"
  local CONFIG_PATH="$6"
  local OUTPUT_DIR="$7"
  local FLEETPY_CONFIG="$8"

  srun --nodes=1 --ntasks=1 --nodelist="${NODE}" --cpus-per-task=48 /bin/bash -lc "
    cd \"${EQASIM_DIR}\" &&
    echo \"[${LABEL}] [\$(date -Is)] Starting MATSim\" > \"${JAVA_LOG}\" &&
    java \
      -Xms100g -Xmx600g \
      -Xlog:gc*,os=info,memops=info \
      -Djava.awt.headless=true \
      -cp bavaria/target/bavaria-2.0.0.jar \
      org.eqasim.bavaria.RunSimulationWithFleetPy \
      --config-path \"${CONFIG_PATH}\" \
      --config:global.numberOfThreads ${MATSim_CPUS} \
      --config:controller.outputDirectory \"${OUTPUT_DIR}\" \
      --use-vdf True \
      --remote-port ${PORT} \
      >> \"${JAVA_LOG}\" 2>&1 &

    if [ \"${MIN_WAIT_SECONDS}\" -gt 0 ]; then
      echo \"[${LABEL}] [\$(date -Is)] Waiting ${MIN_WAIT_SECONDS}s before checking MATSim socket...\" >&2
      sleep \"${MIN_WAIT_SECONDS}\"
    fi
    elapsed=0
    while [ \"\${elapsed}\" -lt \"${MAX_WAIT_SECONDS}\" ]; do
      if (echo > /dev/tcp/127.0.0.1/${PORT}) >/dev/null 2>&1; then
        break
      fi
      if [ \"\$((elapsed % 60))\" -eq 0 ]; then
        echo \"[${LABEL}] [\$(date -Is)] Waiting for MATSim socket on port ${PORT} (\${elapsed}s elapsed)...\" >&2
      fi
      sleep \"${WAIT_INTERVAL_SECONDS}\"
      elapsed=\$((elapsed + WAIT_INTERVAL_SECONDS))
    done

    if ! (echo > /dev/tcp/127.0.0.1/${PORT}) >/dev/null 2>&1; then
      echo \"[${LABEL}] WARNING: MATSim socket not reachable on port ${PORT} after ${MAX_WAIT_SECONDS}s. Starting FleetPy anyway...\" >&2
    fi

    if [ ! -d \"${FLEETPY_DIR}\" ]; then
      echo \"[${LABEL}] ERROR: FleetPy directory not found: ${FLEETPY_DIR}\" >&2
      exit 1
    fi
    if [ ! -f \"${FLEETPY_CONFIG}\" ]; then
      echo \"[${LABEL}] ERROR: FleetPy config not found: ${FLEETPY_CONFIG}\" >&2
      exit 1
    fi
    if [ ! -x \"${FLEETPY_PYTHON}\" ]; then
      echo \"[${LABEL}] ERROR: FleetPy python not executable: ${FLEETPY_PYTHON}\" >&2
      exit 1
    fi

    cd \"${FLEETPY_DIR}\" &&
    echo \"[${LABEL}] [\$(date -Is)] Launching FleetPy\" >&2 &&
    echo \"[\$(date -Is)] Starting FleetPy\" >> \"${FLEETPY_LOG}\" &&
    export OMP_NUM_THREADS=${FLEETPY_CPUS} &&
    export OPENBLAS_NUM_THREADS=${FLEETPY_CPUS} &&
    export MKL_NUM_THREADS=${FLEETPY_CPUS} &&
    \"${FLEETPY_PYTHON}\" src/coupling/MATSimEqasim/MATSimSocket.py \
      \"${FLEETPY_CONFIG}\" \
      \"${NETWORK_PATH}\" \
      ${PORT} \
      >> \"${FLEETPY_LOG}\" 2>&1
    # No trailing '&' here: we want this process to run to completion on this node.
    # The outer script runs the two nodes in parallel via run_one_node ... &
  "
}

# Run both scenarios in parallel on the two nodes.
run_one_node "${NODE0}" "sc1_30" "${PORT_SC1_30}" "${JAVA_LOG_SC1_30}" "${FLEETPY_LOG_SC1_30}" \
  "${CONFIG_PATH_SC1_30}" "${OUTPUT_DIR_SC1_30}" "${FLEETPY_CONFIG_SC1_30}" &
PID0=$!
run_one_node "${NODE1}" "sc_1_100" "${PORT_SC1_100}" "${JAVA_LOG_SC1_100}" "${FLEETPY_LOG_SC1_100}" \
  "${CONFIG_PATH_SC1_100}" "${OUTPUT_DIR_SC1_100}" "${FLEETPY_CONFIG_SC1_100}" &
PID1=$!

if [ -z "${PID0:-}" ] || [ -z "${PID1:-}" ]; then
  echo "ERROR: Failed to launch one or both node tasks (PID0=${PID0:-empty}, PID1=${PID1:-empty})" >&2
  exit 1
fi
echo "[$(date -Is)] Both node tasks launched (PIDs ${PID0}, ${PID1}). Waiting for completion." >&2
wait "$PID0"
RET0=$?
wait "$PID1"
RET1=$?

if [ "$RET0" -ne 0 ]; then
  echo "ERROR: sc1_30 (node ${NODE0}) exited with code ${RET0}" >&2
fi
if [ "$RET1" -ne 0 ]; then
  echo "ERROR: sc_1_100 (node ${NODE1}) exited with code ${RET1}" >&2
fi
if [ "$RET0" -ne 0 ] || [ "$RET1" -ne 0 ]; then
  exit 1
fi
echo "[$(date -Is)] Both scenarios finished successfully." >&2
