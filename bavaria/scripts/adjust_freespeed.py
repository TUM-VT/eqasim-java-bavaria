"""
Preprocessing step for the "Tempo 30 inside the Mittlerer Ring" Munich policy
measure: caps the freespeed of every network link whose start AND end node both
fall inside a given polygon (e.g. the Mittlerer Ring) to a maximum km/h value.
The pre-cap freespeed is preserved as a per-link "eqasim:initialFreespeed"
attribute so the change is always recoverable, and re-running the script is
idempotent (a link that was already capped by a previous run is not
overwritten again).

Usage:
    python adjust_freespeed.py <input_network.xml[.gz]> <output_network.xml[.gz]> \
        <shape_path> <max_freespeed_kmh> \
        [--shape-attribute NAME --shape-value VALUE] [--network-crs EPSG:25832]
"""
import argparse
import gzip
import sys

import geopandas as gpd
from lxml import etree
from shapely.ops import unary_union
from shapely.prepared import prep
from shapely.geometry import Point

INITIAL_FREESPEED_ATTRIBUTE = "eqasim:initialFreespeed"


def load_prepared_polygon(shape_path, shape_attribute, shape_value, network_crs):
    gdf = gpd.read_file(shape_path)

    if shape_attribute is not None:
        gdf = gdf[gdf[shape_attribute] == shape_value]

    if len(gdf) == 0:
        raise ValueError(f"No features in {shape_path} match {shape_attribute}={shape_value!r}")

    # Network node coordinates carry no CRS metadata of their own (MATSim network.xml
    # doesn't encode it), so the polygon must be reprojected into the network's CRS
    # before doing point-in-polygon checks - otherwise the geometries silently don't
    # line up even though both look like plain x/y numbers.
    if network_crs is not None:
        if gdf.crs is None:
            raise ValueError(
                f"{shape_path} has no CRS info (missing .prj?) - cannot verify/convert it to "
                f"match --network-crs {network_crs}"
            )
        if str(gdf.crs) != network_crs:
            print(f"Reprojecting shapefile from {gdf.crs} to {network_crs}")
            gdf = gdf.to_crs(network_crs)
    elif gdf.crs is not None:
        print(f"Warning: shapefile CRS is {gdf.crs} but --network-crs was not given - "
              f"assuming network coordinates are already in this CRS")

    # Dissolve in case the filter still matches several features - and, critically,
    # wrap in a prepared geometry so repeated .contains() calls below use an index
    # instead of a raw unindexed Geometry.contains() loop over every link.
    geometry = unary_union(gdf.geometry)
    return prep(geometry)


def open_maybe_gzip(path, mode):
    if path.endswith(".gz"):
        return gzip.open(path, mode)
    return open(path, mode)


def find_or_create_attributes(element):
    attributes = element.find("attributes")
    if attributes is None:
        attributes = etree.SubElement(element, "attributes")
    return attributes


def find_attribute(attributes, name):
    for attribute in attributes.findall("attribute"):
        if attribute.get("name") == name:
            return attribute
    return None


def adjust_freespeed(network, prepared_polygon, max_freespeed_mps):
    node_coords = {}
    for node in network.findall("nodes/node"):
        node_coords[node.get("id")] = (float(node.get("x")), float(node.get("y")))

    inspected = 0
    adjusted = 0

    for link in network.findall("links/link"):
        inspected += 1

        from_point = Point(node_coords[link.get("from")])
        to_point = Point(node_coords[link.get("to")])

        if not (prepared_polygon.contains(from_point) and prepared_polygon.contains(to_point)):
            continue

        freespeed = float(link.get("freespeed"))
        if freespeed <= max_freespeed_mps:
            continue

        attributes = find_or_create_attributes(link)
        if find_attribute(attributes, INITIAL_FREESPEED_ATTRIBUTE) is None:
            initial = etree.SubElement(attributes, "attribute")
            initial.set("name", INITIAL_FREESPEED_ATTRIBUTE)
            initial.set("class", "java.lang.Double")
            initial.text = link.get("freespeed")

        link.set("freespeed", str(max_freespeed_mps))
        adjusted += 1

    return inspected, adjusted


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input_path")
    parser.add_argument("output_path")
    parser.add_argument("shape_path")
    parser.add_argument("max_freespeed_kmh", type=float)
    parser.add_argument("--shape-attribute", default=None)
    parser.add_argument("--shape-value", default=None)
    parser.add_argument("--network-crs", default=None,
                         help="CRS of the network's x/y coordinates, e.g. EPSG:25832. "
                              "The shapefile is reprojected into this CRS before the point-in-polygon "
                              "check if it differs. Strongly recommended whenever the shapefile's own "
                              "CRS (read from its .prj) isn't already the network's CRS.")
    args = parser.parse_args()

    if (args.shape_attribute is None) != (args.shape_value is None):
        parser.error("--shape-attribute and --shape-value must be given together")

    prepared_polygon = load_prepared_polygon(args.shape_path, args.shape_attribute, args.shape_value,
                                              args.network_crs)
    max_freespeed_mps = args.max_freespeed_kmh / 3.6

    with open_maybe_gzip(args.input_path, "rb") as f:
        tree = etree.parse(f)

    inspected, adjusted = adjust_freespeed(tree.getroot(), prepared_polygon, max_freespeed_mps)
    print(f"Inspected {inspected} links, capped {adjusted} to {args.max_freespeed_kmh} km/h "
          f"({max_freespeed_mps:.4f} m/s)")

    with open_maybe_gzip(args.output_path, "wb") as f:
        tree.write(f, xml_declaration=True, encoding="UTF-8", doctype=tree.docinfo.doctype)
    print(f"Wrote adjusted network to {args.output_path}")


if __name__ == "__main__":
    sys.exit(main())
