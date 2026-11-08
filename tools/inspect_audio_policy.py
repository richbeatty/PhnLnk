#!/usr/bin/env python3
"""Summarize declared telephony routes. Never infers live-call audibility.

Use on a public vendor policy or a sanitized, authorized firmware export.
XInclude dependencies must be supplied separately; this tool never fetches URLs.
"""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def inspect(root):
    modules = []
    for module in root.findall("./modules/module"):
        devices = {p.get("tagName"): p for p in module.findall("./devicePorts/devicePort")}
        mixes = {p.get("name"): p for p in module.findall("./mixPorts/mixPort")}
        inputs = [p for p in devices.values() if p.get("type") in
                  ("AUDIO_DEVICE_IN_TELEPHONY_RX", "AUDIO_DEVICE_IN_VOICE_CALL")]
        outputs = [p for p in devices.values() if p.get("type") == "AUDIO_DEVICE_OUT_TELEPHONY_TX"]
        if not inputs and not outputs:
            continue

        def describe(port):
            return {"name": port.get("tagName", port.get("name")), "type": port.get("type"),
                    "canonical_type": ("AUDIO_DEVICE_IN_TELEPHONY_RX"
                                       if port.get("type") == "AUDIO_DEVICE_IN_VOICE_CALL"
                                       else port.get("type")),
                    "flags": port.get("flags", ""),
                    "profiles": [dict(p.attrib) for p in port.findall("profile")]}

        down = []
        up = []
        for route in module.findall("./routes/route"):
            sink = route.get("sink")
            sources = [s.strip() for s in route.get("sources", "").split(",")]
            if any(p.get("tagName") in sources for p in inputs):
                down.append({"sink": sink, "sources": sources,
                             "capture_mix": describe(mixes[sink]) if sink in mixes else None})
            if any(p.get("tagName") == sink for p in outputs):
                up.append({"sink": sink, "sources": sources,
                           "playback_mixes": [describe(mixes[s]) for s in sources if s in mixes]})
        modules.append({"module": module.get("name"), "hal_version": module.get("halVersion"),
                        "call_input_devices": [describe(p) for p in inputs],
                        "telephony_tx_devices": [describe(p) for p in outputs],
                        "downlink_routes": down, "uplink_routes": up})
    return {"evidence": "DECLARED_POLICY_ONLY_NOT_AUDIBILITY", "modules": modules,
            "unresolved_includes": [p.get("href") for p in root.iter()
                                    if p.tag == "{http://www.w3.org/2001/XInclude}include"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("policy", type=Path)
    args = parser.parse_args()
    # ElementTree does not resolve external entities or XInclude automatically.
    result = inspect(ET.parse(args.policy).getroot())
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
