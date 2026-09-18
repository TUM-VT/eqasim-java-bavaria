"""Regenerate asc_grid_stage4.json.

Stage 4 starts from the stage-3 winner (bb-0.1014_cpb-0.1000) rather than from
stage 2's alphas: those alphas were fitted against the OLD betas, whereas the
winner's alphas were fitted alongside the good ones. Its mvv errors were
walk -0.04, bike -0.14, car_passenger +0.89, car +0.56, pt -1.27 pp.

Two blocks, deliberately separated:

  A  pt_alpha x bike_beta refinement (9 runs) - closes the PT share gap, which
     is the last error above 1pp, and finishes the bike length fit.

  B  car travel-time beta (3 runs) - car trips are ~1.5 km TOO SHORT in every
     run of every stage so far, because car's beta was never touched. Kept in
     its own block because car is the ASC reference: changing its beta moves
     the reference, so every other alpha needs the dU_car re-compensation.

Run this instead of hand-editing the json - the bike alphas and the whole of
block B are derived, so editing values by hand would break the compensation.
"""
import json
from pathlib import Path

OUT = Path(__file__).with_name("asc_grid_stage4.json")

# --- stage-3 winner: bb-0.1014_cpb-0.1000 ------------------------------------
WINNER_ALPHA = {
    "car.alpha_u": 0.0,
    "walk.alpha_u": -0.46,          # gave sh_walk -0.04 -> keep
    "pt.alpha_u": -0.386,
    "bike.alpha_u": -1.3883,        # gave sh_bicycle -0.14 -> keep at this beta
    "bavariaCarPassenger.alpha_u": -1.8798,
    "bavariaDrt.alpha_u": -0.5969,
}
WINNER_BIKE_BETA = -0.1014
CP_BETA = -0.1000                   # confirmed: the OLD value, length error +0.46 km
CAR_BETA_BASE = -0.0822             # current (new survey) value

# car_passenger sat at +0.89pp. The naive update is ln(12/12.889) = -0.072, but
# raising PT pulls car_passenger down as well, so only a partial correction.
CP_ALPHA = -1.93

# PT: ln(target/sim) says +0.073, but the stage-2 sweep showed the real response
# is ~0.5pp per 0.06 utils (~8pp per util), so closing 1.27pp needs ~+0.16.
# -0.35 is kept as a near-status-quo control.
PT_ALPHAS = [-0.23, -0.29, -0.35]

# bike length error by beta (at cp -0.1000): -0.0845 -> +1.26, -0.1014 -> +0.69,
# -0.1183 -> +0.28 km. Optimum is just past -0.1183.
BIKE_BETAS = [-0.1014, -0.1183, -0.1270]

# Compensation basis for bike. The median trip time is 14.3 min, but stage 3
# showed that basis over-compensates slightly (bike share went -0.14 -> +0.43pp
# over one beta step), so the effective basis is reduced.
BIKE_COMP_MIN = 12.0

# --- block B: car beta -------------------------------------------------------
# Car median trip time, for dU_car.
CAR_MEDIAN_MIN = 14.07
# car is 1.5 km too short and needs a WEAKER (less negative) beta. -0.0756 is
# the old pre-survey value; the other two extrapolate, since the car_passenger
# experience suggests ~30% of beta change buys ~2 km of median length.
CAR_BETAS = [-0.0756, -0.0700, -0.0658]


def bike_alpha_for(bike_beta):
    d = -(bike_beta - WINNER_BIKE_BETA) * BIKE_COMP_MIN
    return round(WINNER_ALPHA["bike.alpha_u"] + d, 4)


def build_runs():
    runs = []

    for pt_a in PT_ALPHAS:
        for bb in BIKE_BETAS:
            runs.append({
                "name": f"A_pt{pt_a}_bb{bb:.4f}",
                "parameters": {
                    "pt.alpha_u": pt_a,
                    "bike.betaTravelTime_u_min": round(bb, 4),
                    "bike.alpha_u": bike_alpha_for(bb),
                },
            })

    # Block B sits on the stage-3 winner EXACTLY, so the only difference to an
    # already-measured run is car's beta plus the offset it forces. Block A's
    # pt/bike moves are deliberately NOT mixed in: they are extrapolations, and
    # stacking them here would make a car-beta effect unattributable.
    for car_b in CAR_BETAS:
        # car alpha stays pinned at 0, so car's own utility shift cannot be
        # absorbed by its own ASC and becomes a common offset for everyone else:
        #   alpha_new_m = alpha_old_m + dBeta_car * Xbar_car
        d_u_car = (car_b - CAR_BETA_BASE) * CAR_MEDIAN_MIN

        params = {
            "car.betaTravelTime_u_min": round(car_b, 4),
            "bike.betaTravelTime_u_min": WINNER_BIKE_BETA,
            "pt.alpha_u": round(WINNER_ALPHA["pt.alpha_u"] + d_u_car, 4),
            "walk.alpha_u": round(WINNER_ALPHA["walk.alpha_u"] + d_u_car, 4),
            "bike.alpha_u": round(WINNER_ALPHA["bike.alpha_u"] + d_u_car, 4),
            "bavariaCarPassenger.alpha_u": round(
                WINNER_ALPHA["bavariaCarPassenger.alpha_u"] + d_u_car, 4),
            "bavariaDrt.alpha_u": round(WINNER_ALPHA["bavariaDrt.alpha_u"] + d_u_car, 4),
        }
        runs.append({"name": f"B_carb{car_b:.4f}", "parameters": params})

    return runs


def main():
    runs = build_runs()

    base = dict(WINNER_ALPHA)
    base["pt.alpha_u"] = PT_ALPHAS[1]
    base["bavariaCarPassenger.alpha_u"] = CP_ALPHA
    base["bike.betaTravelTime_u_min"] = WINNER_BIKE_BETA
    base["bavariaCarPassenger.betaInVehicleTravelTime_u_min"] = CP_BETA
    base["car.betaTravelTime_u_min"] = CAR_BETA_BASE

    spec = {
        "_comment": [
            f"Stage-4 sweep. {len(runs)} runs. GENERATED by gen_stage4_grid.py.",
            "Built on the stage-3 winner bb-0.1014_cpb-0.1000 (share_mae 1.19,",
            "dist_mae 0.94), whose mvv errors were walk -0.04, bike -0.14,",
            "car_passenger +0.89, car +0.56, pt -1.27 pp.",
            "It also finally combines the best-known alphas with the best-known",
            "betas - stage 3 ran its betas on the stage-1 alpha update point, so",
            "that pairing has never actually been simulated.",
            "",
            "car_passenger beta is FIXED at -0.1000 for every run. Stage 3 settled",
            "this: -0.0761 (new survey) gave +2.75 km, -0.1000 (old value) +0.46 km,",
            "-0.1300 -1.47 km. The survey's weakening of this coefficient was the",
            "cause of the length error - worth re-checking in the estimation.",
            "",
            "BLOCK A (9 runs): pt_alpha x bike_beta.",
            "  pt: ln(target/sim) suggests only +0.073, but the stage-2 sweep gives",
            "  a real response of ~8pp per util, so ~+0.16 is needed. -0.35 is a",
            "  near-status-quo control to verify that gradient.",
            "  bike: length error was +1.26 / +0.69 / +0.28 km at beta -0.0845 /",
            "  -0.1014 / -0.1183, so the optimum sits just past -0.1183.",
            f"  bike alpha is re-compensated per beta on a {BIKE_COMP_MIN} min basis",
            "  (below the 14.3 min median, because stage 3 showed 14.3 slightly",
            "  over-compensates).",
            "",
            "BLOCK B (3 runs): car travel-time beta, isolated on purpose.",
            "  car trips are ~1.5 km too short in EVERY run so far - its beta was",
            "  never swept. The new survey strengthened it (-0.0756 -> -0.0822),",
            "  the mirror image of what it did to car_passenger, so -0.0756 (the old",
            "  value) and two weaker values are tested.",
            "  Because car.alpha_u is pinned at 0 as the reference, car's own",
            "  utility shift cannot be absorbed by its own ASC, so every other",
            "  alpha takes the common offset dU_car = dBeta_car * 14.07 min.",
            "  Block B sits on the stage-3 winner EXACTLY and does NOT include",
            "  block A's pt/bike moves: those are extrapolations, and stacking them",
            "  would make any car-beta effect unattributable. So compare block B",
            "  against the stage-3 winner (its baseline), and block A against it too.",
            "  It still moves six alphas at once - that is forced by the reference",
            "  pinning, not a free choice.",
            "",
            "Residual share drift is still expected (first-order compensation), so",
            "keep judging share_mae and dist_mae separately.",
            "Suggested: run into a FRESH output directory - _out currently mixes",
            "three stages with overlapping numeric prefixes.",
        ],
        "base": base,
        "extra_args": ["--use-vdf", "true"],
        "runs": runs,
    }

    OUT.write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUT.name} with {len(runs)} runs\n")

    print("BLOCK A")
    print(f"{'run':26} {'pt_a':>7} {'bike_b':>9} {'bike_a':>9}")
    for r in runs:
        if r["name"].startswith("A_"):
            p = r["parameters"]
            print(f"{r['name']:26} {p['pt.alpha_u']:>7} {p['bike.betaTravelTime_u_min']:>9} "
                  f"{p['bike.alpha_u']:>9}")

    print("\nBLOCK B (dU_car offset applied to every non-car alpha)")
    for r in runs:
        if r["name"].startswith("B_"):
            p = r["parameters"]
            d = (p["car.betaTravelTime_u_min"] - CAR_BETA_BASE) * CAR_MEDIAN_MIN
            print(f"{r['name']:26} car_b={p['car.betaTravelTime_u_min']:>8} "
                  f"dU_car={d:+.4f}  pt={p['pt.alpha_u']:>8} walk={p['walk.alpha_u']:>8} "
                  f"bike={p['bike.alpha_u']:>8} cp={p['bavariaCarPassenger.alpha_u']:>8}")


if __name__ == "__main__":
    main()
