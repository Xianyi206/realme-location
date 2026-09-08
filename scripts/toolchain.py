"""Explicit local toolchain configuration; never assume a developer's disk layout."""
import os
from pathlib import Path

def configured_path(name):
    value = os.environ.get(name)
    if not value:
        raise RuntimeError('Set ' + name + ' to your local toolchain directory')
    path = Path(value).expanduser().resolve()
    if not path.is_dir():
        raise RuntimeError(name + ' does not point to an existing directory')
    return path

def java_tool(name):
    return configured_path('JAVA_HOME') / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
