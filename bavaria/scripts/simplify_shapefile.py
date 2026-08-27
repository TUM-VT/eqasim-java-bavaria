"""
One-off utility to simplify the geometry of a polygon shapefile (reduces vertex
count while preserving the approximate shape), used to cut down the cost of the
unindexed Geometry.contains() checks in matsim-contrib-discrete_mode_choice's
ShapeFileConstraint, which is called twice (origin/destination link) for every
trip that tests the "drt" mode, before any routing happens.

Usage:
    python simplify_shapefile.py <input.shp> <output.shp> <toleranceMeters> [--plot out.png]
"""
import argparse
import sys

import geopandas as gpd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from shapely.geometry import Polygon, MultiPolygon
from shapely.ops import unary_union


def count_vertices(geom):
    return sum(
        len(poly.exterior.coords) + sum(len(interior.coords) for interior in poly.interiors)
        for poly in (geom.geoms if geom.geom_type == "MultiPolygon" else [geom])
    )


def exterior_only(geom):
    """Dissolve touching sub-polygons and drop interior rings (holes)."""
    dissolved = unary_union(geom)
    parts = dissolved.geoms if dissolved.geom_type == "MultiPolygon" else [dissolved]
    if len(parts) > 1:
        print(f"WARNING: geometry did not dissolve into a single part ({len(parts)} disjoint parts remain) "
              f"- 'no islands' assumption doesn't hold, keeping them separate")
    exteriors = [Polygon(part.exterior.coords) for part in parts]
    return exteriors[0] if len(exteriors) == 1 else MultiPolygon(exteriors)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input_path")
    parser.add_argument("output_path")
    parser.add_argument("tolerance_meters", type=float, nargs="?", default=0.0)
    parser.add_argument("--exterior-only", action="store_true",
                         help="Dissolve touching sub-polygons and drop interior rings before simplifying")
    parser.add_argument("--plot", default=None, help="Path to write a before/after PNG")
    args = parser.parse_args()

    gdf = gpd.read_file(args.input_path)

    original_vertex_count = sum(count_vertices(geom) for geom in gdf.geometry)

    simplified = gdf.copy()
    if args.exterior_only:
        simplified["geometry"] = simplified.geometry.apply(exterior_only)
    if args.tolerance_meters > 0:
        simplified["geometry"] = simplified.geometry.simplify(args.tolerance_meters, preserve_topology=True)

    simplified_vertex_count = sum(count_vertices(geom) for geom in simplified.geometry)

    print(f"Vertices: {original_vertex_count} -> {simplified_vertex_count} "
          f"({100 * simplified_vertex_count / original_vertex_count:.1f}% remaining)")

    simplified.to_file(args.output_path)
    print(f"Wrote simplified shapefile to {args.output_path}")

    if args.plot:
        fig, axes = plt.subplots(1, 2, figsize=(14, 7), sharex=True, sharey=True)
        gdf.plot(ax=axes[0], edgecolor="black", facecolor="lightblue")
        axes[0].set_title(f"Original ({original_vertex_count} vertices)")
        simplified.plot(ax=axes[1], edgecolor="black", facecolor="lightgreen")
        mode_label = ("exterior-only" if args.exterior_only else "") + \
                     (f"+tolerance={args.tolerance_meters}m" if args.tolerance_meters > 0 else "")
        axes[1].set_title(f"Simplified, {mode_label} ({simplified_vertex_count} vertices)")
        for ax in axes:
            ax.set_aspect("equal")
        fig.tight_layout()
        fig.savefig(args.plot, dpi=150)
        print(f"Wrote before/after plot to {args.plot}")


if __name__ == "__main__":
    sys.exit(main())
