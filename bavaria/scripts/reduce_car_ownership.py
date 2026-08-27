"""
Preprocessing step for the "-20% car ownership for Munich residents" Munich policy
measure: reads a MATSim population, and for a seeded-random share of persons who
are Munich residents (isMunichResident=true) AND currently have a car
(carAvailability != "none"), sets their carAvailability attribute to "none" - i.e.
the share is of car-owning Munich residents, not of all Munich residents.
Writes a new population file; the input is untouched.

Population files can be far larger than a network file (many persons, each with
several plan alternatives), so this streams through the file twice with
lxml.etree.iterparse rather than building a full in-memory tree, keeping peak
memory bounded regardless of population size:
  1. a read-only pass collects the ids of all car-owning Munich residents,
  2. a rewrite pass streams every element back out unchanged, except that persons
     selected in the (seeded, reproducible) random sample get carAvailability="none".

Usage:
    python reduce_car_ownership.py <input_population.xml[.gz]> <output_population.xml[.gz]> \
        --seed 1234 [--share 0.2]
"""
import argparse
import gzip
import random
import re
import sys

from lxml import etree

IS_MUNICH_RESIDENT_ATTRIBUTE = "isMunichResident"
CAR_AVAILABILITY_ATTRIBUTE = "carAvailability"


def open_maybe_gzip(path, mode):
    if path.endswith(".gz"):
        return gzip.open(path, mode)
    return open(path, mode)


def extract_header(input_path):
    """Read the XML declaration and DOCTYPE line verbatim from the input file,
    so the output carries the same declaration/DTD reference instead of a
    hardcoded guess."""
    with open_maybe_gzip(input_path, "rt") as f:
        head = f.read(4096)

    declaration_match = re.search(r"<\?xml[^>]*\?>", head)
    doctype_match = re.search(r"<!DOCTYPE[^>]*>", head, re.DOTALL)

    declaration = declaration_match.group(0) if declaration_match else '<?xml version="1.0" encoding="UTF-8"?>'
    doctype = doctype_match.group(0) if doctype_match else None

    return declaration, doctype


def find_attribute(attributes_elem, name):
    for attribute in attributes_elem.findall("attribute"):
        if attribute.get("name") == name:
            return attribute
    return None


def is_munich_resident(person_elem):
    attributes = person_elem.find("attributes")
    if attributes is None:
        return False
    attribute = find_attribute(attributes, IS_MUNICH_RESIDENT_ATTRIBUTE)
    return attribute is not None and (attribute.text or "").strip().lower() == "true"


def has_car_availability(person_elem):
    attributes = person_elem.find("attributes")
    if attributes is None:
        return False
    attribute = find_attribute(attributes, CAR_AVAILABILITY_ATTRIBUTE)
    return attribute is not None and (attribute.text or "").strip().lower() != "none"


def is_car_owning_munich_resident(person_elem):
    return is_munich_resident(person_elem) and has_car_availability(person_elem)


def set_car_availability_none(person_elem):
    attributes = person_elem.find("attributes")
    if attributes is None:
        attributes = etree.Element("attributes")
        person_elem.insert(0, attributes)  # "attributes" must precede "plan" per the population DTD

    car_availability = find_attribute(attributes, CAR_AVAILABILITY_ATTRIBUTE)
    if car_availability is None:
        car_availability = etree.SubElement(attributes, "attribute")
        car_availability.set("name", CAR_AVAILABILITY_ATTRIBUTE)
        car_availability.set("class", "java.lang.String")

    car_availability.text = "none"


def collect_car_owning_resident_ids(input_path):
    total_persons = 0
    eligible_ids = []

    with open_maybe_gzip(input_path, "rb") as f:
        for _, elem in etree.iterparse(f, events=("end",), tag="person"):
            total_persons += 1
            if is_car_owning_munich_resident(elem):
                eligible_ids.append(elem.get("id"))

            elem.clear()
            while elem.getprevious() is not None:
                del elem.getparent()[0]

    return total_persons, eligible_ids


def rewrite_population(input_path, output_path, selected_ids, declaration, doctype):
    converted = 0

    with open_maybe_gzip(input_path, "rb") as fin, open_maybe_gzip(output_path, "wb") as fout:
        fout.write((declaration + "\n").encode("utf-8"))
        if doctype:
            fout.write((doctype + "\n").encode("utf-8"))

        with etree.xmlfile(fout) as xf:
            with xf.element("population"):
                for _, elem in etree.iterparse(fin, events=("end",)):
                    parent = elem.getparent()
                    if parent is None or parent.tag != "population":
                        continue  # not a direct child of the root - will be serialized with its parent

                    if elem.tag == "person" and elem.get("id") in selected_ids:
                        set_car_availability_none(elem)
                        converted += 1

                    xf.write(elem)
                    elem.clear()
                    while elem.getprevious() is not None:
                        del elem.getparent()[0]

    return converted


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input_path")
    parser.add_argument("output_path")
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--share", type=float, default=0.2)
    args = parser.parse_args()

    declaration, doctype = extract_header(args.input_path)

    total_persons, eligible_ids = collect_car_owning_resident_ids(args.input_path)
    eligible_ids.sort()  # deterministic order, independent of on-disk person order

    sample_size = int(len(eligible_ids) * args.share)
    selected_ids = set(random.Random(args.seed).sample(eligible_ids, sample_size))

    converted = rewrite_population(args.input_path, args.output_path, selected_ids, declaration, doctype)

    print(f"Persons scanned: {total_persons}")
    print(f"Car-owning Munich residents: {len(eligible_ids)}")
    print(f"Converted to carAvailability=none: {converted} "
          f"({100 * converted / len(eligible_ids):.1f}% of car-owning residents, target share {100 * args.share:.1f}%)")
    print(f"Seed: {args.seed}")
    print(f"Wrote adjusted population to {args.output_path}")


if __name__ == "__main__":
    sys.exit(main())
