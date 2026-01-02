import argparse
import pathlib
import subprocess
import sys

from vb2arduino import transpile_string


def write_file(path: pathlib.Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def run_cmd(cmd: list[str]) -> int:
    print("[cmd]", " ".join(cmd))
    return subprocess.call(cmd)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="VB6-like to Arduino transpiler")
    parser.add_argument("input", help="VB-like source file")
    parser.add_argument("--out", default="generated", help="Output directory")
    parser.add_argument("--board", help="PlatformIO board id (e.g., esp32-s3-devkitm-1)")
    parser.add_argument("--build", action="store_true", help="Run 'pio run' after transpiling")
    parser.add_argument("--upload", action="store_true", help="Run 'pio run --target upload'")
    parser.add_argument("--port", help="Upload port for PlatformIO")
    args = parser.parse_args(argv)

    src_path = pathlib.Path(args.input)
    out_dir = pathlib.Path(args.out)
    out_cpp = out_dir / "main.cpp"

    cpp = transpile_string(src_path.read_text(encoding="utf-8"))
    write_file(out_cpp, cpp)
    print(f"[ok] Transpiled to {out_cpp}")

    if not args.build and not args.upload:
        return 0

    if not args.board:
        print("[error] --board is required for build/upload", file=sys.stderr)
        return 1

    env_args = []
    if args.port:
        env_args.extend(["--upload-port", args.port])

    # Create minimal platformio.ini if missing
    pio_ini = out_dir / "platformio.ini"
    if not pio_ini.exists():
        pio_ini.write_text(
            f"""[env:{args.board}]
platform = espressif32
board = {args.board}
framework = arduino
build_src_dir = .
""",
            encoding="utf-8",
        )
        print(f"[init] Wrote {pio_ini}")

    cmd = ["pio", "run", "--project-dir", str(out_dir), "--environment", args.board]
    if args.upload:
        cmd.extend(["--target", "upload"])
    cmd.extend(env_args)

    return run_cmd(cmd)


if __name__ == "__main__":
    raise SystemExit(main())
