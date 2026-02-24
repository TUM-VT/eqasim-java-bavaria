#!/usr/bin/env python3
import argparse
import gzip
import shutil
from collections import defaultdict
from pathlib import Path

import pandas as pd
import geopandas as gpd


def repo_root() -> Path:
    return Path(__file__).resolve().parents[4]


DEFAULT_INPUT_GZ = Path("../../../simulation_output/eqasim_trips.csv")
DEFAULT_ZONE_SHP = Path(
    "populations/muenchen_2024_10pct/"
    "synthetic_population_output/Munich_mittlererWesten_zone/"
    "polygon_definition.shp"
)

MODE_CANDIDATES = ["mode", "main_mode", "trip_mode", "leg_mode"]
ORIGIN_CANDIDATES = [
    ("from_x", "from_y"),
    ("start_x", "start_y"),
    ("orig_x", "orig_y"),
    ("origin_x", "origin_y"),
    ("x", "y"),
]
DISTANCE_CANDIDATES = [
    "routed_distance",
    "vehicle_distance",
    "distance_m",
    "distance",
    "travel_distance",
    "trav_dist",
    "trip_distance",
    "leg_distance",
    "euclidean_distance",
]
TIME_CANDIDATES = [
    "travel_time_s",
    "travel_time",
    "duration_s",
    "duration",
    "time_s",
    "leg_time",
    "trip_time",
]


def copy_and_unpack(input_gz: Path, work_dir: Path) -> Path:
    work_dir.mkdir(parents=True, exist_ok=True)
    if input_gz.suffix == ".gz":
        target_gz = work_dir / input_gz.name
        if input_gz.resolve() != target_gz.resolve():
            shutil.copy2(input_gz, target_gz)
        target_csv = target_gz.with_suffix("")
        if not target_csv.exists() or target_csv.stat().st_mtime < target_gz.stat().st_mtime:
            with gzip.open(target_gz, "rb") as f_in, open(target_csv, "wb") as f_out:
                shutil.copyfileobj(f_in, f_out)
        return target_csv
    target_csv = work_dir / input_gz.name
    if input_gz.resolve() != target_csv.resolve():
        shutil.copy2(input_gz, target_csv)
    return target_csv


def pick_column(columns, candidates):
    lower = {c.lower(): c for c in columns}
    for cand in candidates:
        if isinstance(cand, tuple):
            a, b = cand
            if a.lower() in lower and b.lower() in lower:
                return lower[a.lower()], lower[b.lower()]
        else:
            if cand.lower() in lower:
                return lower[cand.lower()]
    return None


def load_zone(zone_shp: Path, csv_crs: str | None) -> tuple[gpd.GeoSeries, str | None]:
    zone_gdf = gpd.read_file(zone_shp)
    if zone_gdf.empty:
        raise ValueError("Zone shapefile has no features.")
    zone_geom = zone_gdf.geometry.union_all()
    zone_crs = zone_gdf.crs
    if zone_crs is None and csv_crs is None:
        raise ValueError(
            "Zone CRS is missing and no --csv-crs provided. "
            "Provide CRS so spatial filtering is valid."
        )
    if zone_crs is None and csv_crs is not None:
        zone_crs = csv_crs
    return gpd.GeoSeries([zone_geom], crs=zone_crs), zone_crs


def detect_delimiter(csv_path: Path) -> str:
    with open(csv_path, "r", encoding="utf-8", errors="replace") as f_in:
        header = f_in.readline()
    if ";" in header and "," not in header:
        return ";"
    if "," in header and ";" not in header:
        return ","
    return ";"


def process_csv(
    csv_path: Path,
    zone: gpd.GeoSeries | None,
    zone_crs: str | None,
    csv_crs: str | None,
    metric: str,
    chunk_size: int,
    delimiter: str,
) -> tuple[dict[str, float], float]:
    totals = defaultdict(float)
    total_all = 0.0

    mode_col = None
    origin_cols = None
    metric_col = None

    for chunk in pd.read_csv(
        csv_path, chunksize=chunk_size, low_memory=False, sep=delimiter
    ):
        if mode_col is None:
            mode_col = pick_column(chunk.columns, MODE_CANDIDATES)
            if not mode_col:
                raise ValueError(f"No mode column found. Available: {list(chunk.columns)}")
            origin_cols = pick_column(chunk.columns, ORIGIN_CANDIDATES)
            if not origin_cols:
                raise ValueError(
                    f"No origin coordinate columns found. Available: {list(chunk.columns)}"
                )
            if metric == "distance":
                metric_col = pick_column(chunk.columns, DISTANCE_CANDIDATES)
                if not metric_col:
                    raise ValueError(
                        f"No distance column found. Available: {list(chunk.columns)}"
                    )
            elif metric == "time":
                metric_col = pick_column(chunk.columns, TIME_CANDIDATES)
                if not metric_col:
                    raise ValueError(
                        f"No time column found. Available: {list(chunk.columns)}"
                    )

        if zone is not None:
            x_col, y_col = origin_cols
            points = gpd.GeoSeries(
                gpd.points_from_xy(chunk[x_col], chunk[y_col]),
                crs=csv_crs or zone_crs,
            )
            if zone_crs is not None and points.crs is not None and points.crs != zone_crs:
                points = points.to_crs(zone_crs)
            mask = points.within(zone.iloc[0])
            filtered = chunk.loc[mask]
        else:
            filtered = chunk

        if filtered.empty:
            continue

        if metric == "trips":
            grouped = filtered.groupby(mode_col).size()
            for mode, count in grouped.items():
                totals[str(mode)] += float(count)
                total_all += float(count)
        else:
            values = pd.to_numeric(filtered[metric_col], errors="coerce").fillna(0.0)
            grouped = values.groupby(filtered[mode_col]).sum()
            for mode, value in grouped.items():
                totals[str(mode)] += float(value)
                total_all += float(value)

    return totals, total_all


def main():
    parser = argparse.ArgumentParser(
        description="Compute zone-based modal split by trip origin."
    )
    parser.add_argument("--input-gz", default=DEFAULT_INPUT_GZ)
    parser.add_argument("--work-dir", default=Path("bavaria/src/main/python"))
    parser.add_argument("--zone-shp", default=DEFAULT_ZONE_SHP)
    parser.add_argument(
        "--metric",
        choices=["trips", "distance", "time"],
        default="trips",
        help="Modal split basis.",
    )
    parser.add_argument(
        "--csv-crs",
        default=None,
        help="CRS of CSV coordinates, e.g. EPSG:25832. Required if zone CRS missing.",
    )
    parser.add_argument(
        "--delimiter",
        default=None,
        help="CSV delimiter (auto-detected if omitted).",
    )
    parser.add_argument("--chunk-size", type=int, default=500_000)
    args = parser.parse_args()

    root = repo_root()
    input_gz = (root / Path(args.input_gz)).resolve()
    work_dir = (root / Path(args.work_dir)).resolve()
    zone_shp = (root / Path(args.zone_shp)).resolve()

    if not input_gz.exists():
        raise FileNotFoundError(f"Input not found: {input_gz}")
    if not zone_shp.exists():
        raise FileNotFoundError(f"Zone shapefile not found: {zone_shp}")

    csv_path = copy_and_unpack(input_gz, work_dir)
    zone, zone_crs = load_zone(zone_shp, args.csv_crs)

    delimiter = args.delimiter or detect_delimiter(csv_path)
    totals_zone, total_all_zone = process_csv(
        csv_path=csv_path,
        zone=zone,
        zone_crs=zone_crs,
        csv_crs=args.csv_crs,
        metric=args.metric,
        chunk_size=args.chunk_size,
        delimiter=delimiter,
    )
    totals_all, total_all = process_csv(
        csv_path=csv_path,
        zone=None,
        zone_crs=zone_crs,
        csv_crs=args.csv_crs,
        metric=args.metric,
        chunk_size=args.chunk_size,
        delimiter=delimiter,
    )

    if total_all_zone == 0:
        raise ValueError("No trips found inside the zone with the given filter.")
    if total_all == 0:
        raise ValueError("No trips found in the full dataset.")

    target_modes = {"ride_pooling", "drt", "rideshare"}
    results_zone = []
    for mode, value in sorted(totals_zone.items(), key=lambda x: -x[1]):
        share = value / total_all_zone
        results_zone.append((mode, value, share))

    results_all = []
    for mode, value in sorted(totals_all.items(), key=lambda x: -x[1]):
        share = value / total_all
        results_all.append((mode, value, share))

    output_zone = work_dir / "mode_split_mittlerer_westen_drt.csv"
    with open(output_zone, "w", encoding="utf-8") as f_out:
        f_out.write("mode,value,share\n")
        for mode, value, share in results_zone:
            f_out.write(f"{mode},{value},{share:.8f}\n")

    output_all = work_dir / "mode_split_munich_drt.csv"
    with open(output_all, "w", encoding="utf-8") as f_out:
        f_out.write("mode,value,share\n")
        for mode, value, share in results_all:
            f_out.write(f"{mode},{value},{share:.8f}\n")

    print(f"Total ({args.metric}) in zone: {total_all_zone:.4f}")
    for mode, value, share in results_zone:
        flag = " <= target" if mode in target_modes else ""
        print(f"{mode}: {value:.4f} ({share*100:.4f}%)" + flag)

    ride_pooling_share = totals_zone.get("ride_pooling", 0.0) / total_all_zone
    print(f"ride_pooling >= 1%: {ride_pooling_share >= 0.01}")
    print(f"Results written to: {output_zone}")
    print(f"All-Munich results written to: {output_all}")


if __name__ == "__main__":
    main()
