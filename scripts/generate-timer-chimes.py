#!/usr/bin/env python3
"""Regenerate KIN's original notification sounds with Python's standard library."""
import math
import struct
import wave
from pathlib import Path

DESTINATION = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
SAMPLE_RATE = 22050

for name, notes, duration, amplitude in [
    ('break_chime', [660, 880], .18, .22),
    ('focus_chime', [523.25, 659.25], .22, .14),
]:
    samples = []
    for frequency in notes:
        for index in range(int(SAMPLE_RATE * duration)):
            t = index / SAMPLE_RATE
            envelope = min(1, t / .015) * max(0, 1 - t / duration) ** 1.7
            tone = (math.sin(2 * math.pi * frequency * t) + .15 * math.sin(4 * math.pi * frequency * t)) / 1.15
            samples.append(int(32767 * amplitude * envelope * tone))
        samples.extend([0] * int(SAMPLE_RATE * .05))
    DESTINATION.mkdir(parents=True, exist_ok=True)
    with wave.open(str(DESTINATION / (name + '.wav')), 'wb') as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(struct.pack('<' + 'h' * len(samples), *samples))
