#!/bin/python3

import os, json

lines = []

with open("en_us.json", "r", encoding='utf-8') as file:
    for line in file:
        stripped = line.strip()
        if not stripped:
            lines.append("")
            continue

        if stripped[0] == "{" or stripped[0] == "}":
            continue
        if not stripped[0] == "\"":
            raise Exception("Expected \" at start of " + stripped)
        stripped = stripped[1:]

        size = len(stripped)
        key_end = -1
        escaping = True

        for i in range(size):
            if escaping:
                escaping = False
                continue

            if stripped[i] == "\\":
                escaping = True
            if stripped[i] == "\"":
                key_end = i
                break

        if key_end < 0:
            raise Exception("Unable to find key_end for " + stripped)
        lines.append(stripped[:key_end])

for file in os.listdir("."):
    if not os.path.isfile(file) or not file.endswith(".json"):
        continue
    if file == "en_us.json":
        continue
    with open(file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    with open(file, "w", encoding='utf-8') as f:
        f.write("{\n")
        for index, key in enumerate(lines):
            if not key:
                f.write("\n")
                continue

            
            if not key in data:
                f.write("  \"" + key + "\": null")
            else:
                f.write("  \"" + key + "\": " + json.dumps(data[key], ensure_ascii=False))
            if index < len(lines)-1:
                f.write(",")
            f.write("\n")
        f.write("}")

