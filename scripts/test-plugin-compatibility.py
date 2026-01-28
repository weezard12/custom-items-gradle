#!/usr/bin/env python3
import argparse
import json
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path
from threading import Thread
from typing import Dict, List, Optional, Set, Tuple
import subprocess


class Logger:
    def __init__(self, log_file: Optional[Path]) -> None:
        self._log_file = log_file
        if self._log_file:
            self._log_file.parent.mkdir(parents=True, exist_ok=True)

    def log(self, message: str, level: str = "INFO") -> None:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        line = f"[{timestamp}] [{level}] {message}"
        print(line)
        if self._log_file:
            try:
                with self._log_file.open("a", encoding="utf-8") as handle:
                    handle.write(line + "\n")
            except Exception:
                print(f"[{timestamp}] [ERROR] Failed to write log file: {self._log_file}")


def resolve_project_root(root_candidate: Optional[str]) -> Path:
    try:
        if root_candidate and root_candidate.strip():
            candidate = Path(root_candidate)
            if candidate.exists():
                return candidate.resolve()
            return candidate.expanduser().resolve()
        return (Path(__file__).resolve().parent / "..").resolve()
    except Exception as exc:
        raise RuntimeError(f"Failed to resolve project root: {exc}") from exc


def resolve_optional_path(path_candidate: Optional[str], base_path: Optional[Path]) -> Optional[Path]:
    try:
        if not path_candidate or not path_candidate.strip():
            return None
        candidate = Path(path_candidate)
        if not candidate.is_absolute() and base_path:
            candidate = base_path / candidate
        return candidate.expanduser().resolve()
    except Exception as exc:
        raise RuntimeError(f"Failed to resolve path '{path_candidate}': {exc}") from exc


def get_base_version(version_name: str) -> str:
    if not version_name:
        return version_name
    return version_name.split("-")[0]


def parse_version_key(version_name: str) -> Tuple[int, ...]:
    base = get_base_version(version_name)
    if not base:
        return (0,)
    parts = []
    for piece in base.split("."):
        try:
            parts.append(int(re.match(r"(\d+)", piece).group(1)))
        except Exception:
            parts.append(0)
    return tuple(parts) if parts else (0,)


def get_jdk_major_for_version(version: str, logger: Logger) -> int:
    try:
        base = get_base_version(version)
        if not base:
            return 21
        parts = base.split(".")
        if len(parts) < 2:
            return 21
        minor = int(parts[1])
        patch = int(parts[2]) if len(parts) >= 3 else 0

        if minor <= 16:
            return 8
        if minor == 17:
            return 16
        if minor in (18, 19):
            return 17
        if minor == 20:
            return 21 if patch >= 5 else 17
        return 21
    except Exception as exc:
        logger.log(f"Error determining JDK version for {version}, defaulting to JDK 21: {exc}", "WARN")
        return 21


def resolve_java_home(jdk_major: int, project_root: Path) -> Optional[Path]:
    env_var = f"JAVA{jdk_major}_HOME"
    java_home = os.environ.get(env_var)
    if java_home:
        candidate = Path(java_home)
        if (candidate / "bin" / "java.exe").exists() or (candidate / "bin" / "java").exists():
            return candidate.resolve()

    default_root = project_root / "jdks" / f"jdk{jdk_major}"
    if default_root.exists():
        return default_root.resolve()

    fallback = project_root / f"jdk{jdk_major}"
    if fallback.exists():
        return fallback.resolve()

    return None


def get_server_port(server_dir: Path, logger: Logger) -> int:
    props_path = server_dir / "server.properties"
    if not props_path.exists():
        return 25565
    try:
        for line in props_path.read_text(encoding="utf-8", errors="ignore").splitlines():
            raw = line.strip()
            if not raw or raw.startswith("#"):
                continue
            if "=" in raw:
                key, value = raw.split("=", 1)
            elif ":" in raw:
                key, value = raw.split(":", 1)
            else:
                continue
            if key.strip() == "server-port":
                return int(value.strip())
    except Exception as exc:
        logger.log(f"Failed to read server.properties in {server_dir}: {exc}", "WARN")
    return 25565


def find_listening_pids_for_ports(ports: Set[int], logger: Logger) -> Dict[int, Set[int]]:
    port_map: Dict[int, Set[int]] = {}
    if not ports:
        return port_map
    try:
        result = subprocess.run(
            ["netstat", "-ano", "-p", "tcp"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        if result.returncode != 0:
            logger.log(f"netstat failed: {result.stderr.strip()}", "WARN")
            return port_map
        for line in result.stdout.splitlines():
            raw = line.strip()
            if not raw or not raw.upper().startswith("TCP"):
                continue
            parts = raw.split()
            if len(parts) < 5:
                continue
            local = parts[1]
            state = parts[3].upper()
            pid_text = parts[4]
            if state != "LISTENING":
                continue
            if ":" not in local:
                continue
            port_text = local.rsplit(":", 1)[-1]
            if not port_text.isdigit():
                continue
            port = int(port_text)
            if port not in ports:
                continue
            try:
                pid = int(pid_text)
            except ValueError:
                continue
            port_map.setdefault(port, set()).add(pid)
    except Exception as exc:
        logger.log(f"Failed to read active ports: {exc}", "WARN")
    return port_map


def get_process_info(pid: int) -> Tuple[Optional[str], Optional[str]]:
    try:
        command = (
            f"Get-CimInstance Win32_Process -Filter \"ProcessId={pid}\" "
            "| Select-Object Name,CommandLine | ConvertTo-Json -Compress"
        )
        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command", command],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        if result.returncode != 0 or not result.stdout.strip():
            return None, None
        data = json.loads(result.stdout)
        if isinstance(data, list):
            data = data[0] if data else {}
        name = data.get("Name")
        cmd = data.get("CommandLine")
        return name, cmd
    except Exception:
        return None, None


def terminate_existing_servers(ports: Set[int], logger: Logger) -> None:
    if not ports:
        logger.log("No server ports detected for cleanup", "DEBUG")
        return

    ports_list = ", ".join(str(p) for p in sorted(ports))
    logger.log(f"Checking for existing servers on ports: {ports_list}", "DEBUG")

    port_map = find_listening_pids_for_ports(ports, logger)
    if not port_map:
        logger.log("No existing server processes detected", "DEBUG")
        return

    pid_to_ports: Dict[int, Set[int]] = {}
    for port, pids in port_map.items():
        for pid in pids:
            pid_to_ports.setdefault(pid, set()).add(port)

    for pid, pid_ports in sorted(pid_to_ports.items()):
        if pid == os.getpid() or pid <= 4:
            continue
        name, cmd = get_process_info(pid)
        desc = name or "unknown process"
        if cmd:
            desc = f"{desc} | {cmd}"
        port_list = ", ".join(str(p) for p in sorted(pid_ports))
        logger.log(f"Terminating PID {pid} on ports {port_list} ({desc})", "WARN")
        result = subprocess.run(
            ["taskkill", "/PID", str(pid), "/T", "/F"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        if result.returncode != 0:
            msg = result.stdout.strip() or result.stderr.strip()
            logger.log(f"Failed to terminate PID {pid}: {msg}", "WARN")

    time.sleep(1)
    remaining = find_listening_pids_for_ports(ports, logger)
    if remaining:
        logger.log("Some ports are still in use after cleanup", "WARN")


def read_stream(stream, collector: List[str]) -> None:
    try:
        for line in iter(stream.readline, ""):
            if line == "":
                break
            collector.append(line.rstrip("\r\n"))
    except Exception:
        return


def test_server_with_plugin(
    java_exe: Path,
    jar_path: Path,
    work_dir: Path,
    xms: str,
    xmx: str,
    boot_wait_seconds: int,
    shutdown_wait_seconds: int,
    logger: Logger,
):
    process = None
    stdout_lines: List[str] = []
    stderr_lines: List[str] = []

    try:
        if not java_exe.exists():
            raise RuntimeError(f"Java executable not found: {java_exe}")
        if not jar_path.exists():
            raise RuntimeError(f"Server jar not found: {jar_path}")
        if not work_dir.exists():
            raise RuntimeError(f"Working directory not found: {work_dir}")

        args = [
            str(java_exe),
            f"-Xms{xms}",
            f"-Xmx{xmx}",
            "-jar",
            str(jar_path),
            "--nogui",
        ]

        process = subprocess.Popen(
            args,
            cwd=str(work_dir),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            stdin=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )

        stdout_thread = Thread(target=read_stream, args=(process.stdout, stdout_lines), daemon=True)
        stderr_thread = Thread(target=read_stream, args=(process.stderr, stderr_lines), daemon=True)
        stdout_thread.start()
        stderr_thread.start()

        logger.log(f"Server started (PID: {process.pid}), waiting {boot_wait_seconds} seconds for boot...", "DEBUG")

        deadline = time.time() + boot_wait_seconds
        while time.time() < deadline:
            if process.poll() is not None:
                break
            time.sleep(0.2)

        if process.poll() is None:
            logger.log("Stopping server...", "DEBUG")
            try:
                if process.stdin:
                    process.stdin.write("stop\n")
                    process.stdin.flush()
            except Exception:
                logger.log("Failed to send stop command to server", "WARN")

            shutdown_deadline = time.time() + shutdown_wait_seconds
            while time.time() < shutdown_deadline:
                if process.poll() is not None:
                    break
                time.sleep(0.2)

        if process.poll() is None:
            logger.log("Terminating server process...", "WARN")
            try:
                process.terminate()
            except Exception:
                logger.log("Failed to terminate server process", "WARN")

        if process.poll() is None:
            try:
                process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                logger.log("Force killing server process...", "ERROR")
                try:
                    process.kill()
                except Exception:
                    logger.log("Failed to kill server process", "ERROR")

        exit_code = process.wait(timeout=5)

        if process.stdout:
            process.stdout.close()
        if process.stderr:
            process.stderr.close()
        if process.stdin:
            process.stdin.close()

        stdout_thread.join(timeout=2)
        stderr_thread.join(timeout=2)

        return {
            "exit_code": exit_code,
            "stdout": stdout_lines,
            "stderr": stderr_lines,
        }
    except Exception as exc:
        logger.log(f"Critical error during server test: {exc}", "ERROR")
        if process and process.poll() is None:
            try:
                process.kill()
            except Exception:
                pass
        return {
            "exit_code": -1,
            "stdout": stdout_lines,
            "stderr": stderr_lines,
        }


def analyze_output(stdout_lines: List[str], stderr_lines: List[str]):
    error_patterns = [
        r"Error loading plugin",
        r"Error enabling plugin",
        r"Could not load plugin",
        r"Plugin .* does not have a main class",
        r"Exception.*loading plugin",
        r"Caused by:.*Exception",
        r"at org\.bukkit",
        r"at net\.minecraft",
        r"NoClassDefFoundError",
        r"ClassNotFoundException",
        r"NoSuchMethodError",
        r"UnsupportedClassVersionError",
        r"IncompatibleClassChangeError",
        r"LinkageError",
        r"\[SEVERE\]",
        r"\[ERROR\]",
    ]

    console_error_patterns = [
        r"\[[^\]]+/(ERROR|SEVERE)\]",
        r"\[(ERROR|SEVERE)\]",
    ]

    loaded_patterns = [
        r"Loading .* v\d",
        r"Loaded plugin",
        r"Loading plugin",
    ]

    enabled_patterns = [
        r"Enabling .* v\d",
        r"Enabled plugin",
        r"Enabling plugin",
    ]

    errors: List[str] = []
    warnings: List[str] = []
    console_errors: List[str] = []
    plugin_loaded = False
    plugin_enabled = False

    combined = list(stdout_lines) + list(stderr_lines)

    for line in combined:
        if not line or not line.strip():
            continue
        trimmed = line.strip()

        if not plugin_loaded:
            for pattern in loaded_patterns:
                if re.search(pattern, trimmed):
                    plugin_loaded = True
                    break

        if not plugin_enabled:
            for pattern in enabled_patterns:
                if re.search(pattern, trimmed):
                    plugin_enabled = True
                    break

        for pattern in console_error_patterns:
            if re.search(pattern, trimmed):
                console_errors.append(trimmed)
                break

        for pattern in error_patterns:
            if re.search(pattern, trimmed):
                errors.append(trimmed)
                break

        if "[WARN]" in trimmed or "[WARNING]" in trimmed:
            warnings.append(trimmed)

    return {
        "plugin_loaded": plugin_loaded,
        "plugin_enabled": plugin_enabled,
        "errors": errors,
        "warnings": warnings,
        "console_errors": console_errors,
        "total_lines": len(combined),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Test plugin compatibility against Paper server versions.")
    parser.add_argument("--project-root", dest="project_root")
    parser.add_argument("--config-file", dest="config_file")
    parser.add_argument("--servers-root", dest="servers_root")
    parser.add_argument("--xms", dest="xms", default="1G")
    parser.add_argument("--xmx", dest="xmx", default="1G")
    parser.add_argument("--boot-wait-seconds", dest="boot_wait_seconds", type=int, default=30)
    parser.add_argument("--shutdown-wait-seconds", dest="shutdown_wait_seconds", type=int, default=10)
    parser.add_argument("--only-versions", dest="only_versions")
    args = parser.parse_args()

    project_root = resolve_project_root(args.project_root)
    config_path = resolve_optional_path(args.config_file, project_root) if args.config_file else project_root / "scripts" / "test-paper-config.json"
    config = {}

    if config_path and config_path.exists():
        try:
            config = json.loads(config_path.read_text(encoding="utf-8"))
        except Exception as exc:
            raise RuntimeError(f"Failed to parse config file: {exc}") from exc
    elif args.config_file:
        raise RuntimeError(f"Config file not found: {config_path}")

    if not args.project_root and config.get("projectRoot"):
        project_root = resolve_project_root(config.get("projectRoot"))

    if not args.servers_root and config.get("serversRoot"):
        args.servers_root = config.get("serversRoot")

    if not args.only_versions and config.get("onlyVersions"):
        args.only_versions = config.get("onlyVersions")

    if args.xms == "1G" and config.get("xms"):
        args.xms = config.get("xms")

    if args.xmx == "1G" and config.get("xmx"):
        args.xmx = config.get("xmx")

    if args.boot_wait_seconds == 30 and config.get("bootWaitSeconds") is not None:
        args.boot_wait_seconds = int(config.get("bootWaitSeconds"))

    if args.shutdown_wait_seconds == 10 and config.get("shutdownWaitSeconds") is not None:
        args.shutdown_wait_seconds = int(config.get("shutdownWaitSeconds"))

    log_dir = project_root / "scripts" / "logs"
    log_file = log_dir / f"plugin-test-{datetime.now().strftime('%Y%m%d-%H%M%S')}.log"
    logger = Logger(log_file)

    servers_root = resolve_optional_path(args.servers_root, project_root) if args.servers_root else project_root / "paper-servers"
    if not servers_root.exists():
        raise RuntimeError(
            f"Servers root directory not found: {servers_root}\n\n"
            "Please specify the correct path using --servers-root or in the config file.\n"
            f"Example: python scripts/test-plugin-compatibility.py --servers-root \"G:\\Path\\To\\Servers\"\n"
            "Or add \"serversRoot\": \"G:\\\\Path\\\\To\\\\Servers\" to scripts/test-paper-config.json"
        )

    logger.log("===============================================")
    logger.log("Plugin Compatibility Testing Script (Python)")
    logger.log("===============================================")
    logger.log(f"Project root: {project_root}")
    logger.log(f"Config file: {config_path if config_path and config_path.exists() else '(none)'}")
    logger.log(f"Servers root: {servers_root}")
    logger.log(f"Memory settings: -Xms{args.xms} -Xmx{args.xmx}")
    logger.log(f"Boot wait: {args.boot_wait_seconds} seconds")
    logger.log(f"Shutdown wait: {args.shutdown_wait_seconds} seconds")
    logger.log("===============================================")

    server_dirs = [entry for entry in servers_root.iterdir() if entry.is_dir()]
    server_dirs.sort(key=lambda entry: (parse_version_key(entry.name), entry.name), reverse=True)

    if args.only_versions:
        requested = [version.strip() for version in args.only_versions.split(",") if version.strip()]
        server_dirs = [entry for entry in server_dirs if entry.name in requested]

    if not server_dirs:
        raise RuntimeError(f"No server directories found in: {servers_root}")

    logger.log(f"Found {len(server_dirs)} server(s) to test")
    logger.log("")

    ports: Set[int] = set()
    for server_dir in server_dirs:
        ports.add(get_server_port(server_dir, logger))
    terminate_existing_servers(ports, logger)

    results = []

    for server_dir in server_dirs:
        version = server_dir.name
        server_path = server_dir
        status = "UNKNOWN"
        details: List[str] = []

        logger.log("===============================================")
        logger.log(f"Testing version: {version}")
        logger.log("===============================================")

        try:
            jar_path = server_path / "paper.jar"
            if not jar_path.exists():
                raise RuntimeError(f"Server jar not found: {jar_path}")

            plugins_dir = server_path / "plugins"
            if not plugins_dir.exists():
                raise RuntimeError("Plugins directory not found - server may not be initialized")

            plugin_jars = list(plugins_dir.glob("*.jar"))
            if not plugin_jars:
                raise RuntimeError("No plugin jar found in plugins directory")

            logger.log(f"Found {len(plugin_jars)} plugin(s): {', '.join(jar.name for jar in plugin_jars)}")

            jdk_major = get_jdk_major_for_version(version, logger)
            logger.log(f"Required JDK version: {jdk_major}")

            java_home = resolve_java_home(jdk_major, project_root)
            if not java_home:
                raise RuntimeError(f"Missing JDK {jdk_major} (set JAVA{jdk_major}_HOME or place in jdks\\jdk{jdk_major})")

            java_exe = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
            if not java_exe.exists():
                raise RuntimeError(f"java executable not found: {java_exe}")

            logger.log(f"Using Java: {java_home}")
            logger.log("Starting server...")

            run_result = test_server_with_plugin(
                java_exe=java_exe,
                jar_path=jar_path,
                work_dir=server_path,
                xms=args.xms,
                xmx=args.xmx,
                boot_wait_seconds=args.boot_wait_seconds,
                shutdown_wait_seconds=args.shutdown_wait_seconds,
                logger=logger,
            )

            logger.log(f"Server stopped. Exit code: {run_result['exit_code']}")

            logger.log("Analyzing output...")
            analysis = analyze_output(run_result["stdout"], run_result["stderr"])

            logger.log(f"Plugin loaded: {analysis['plugin_loaded']}")
            logger.log(f"Plugin enabled: {analysis['plugin_enabled']}")

            all_errors = []
            for err in analysis["errors"]:
                if err not in all_errors:
                    all_errors.append(err)
            for err in analysis["console_errors"]:
                if err not in all_errors:
                    all_errors.append(err)

            if analysis["console_errors"]:
                logger.log("Server console errors:", "ERROR")
                for line in analysis["console_errors"]:
                    logger.log(f"  {line}", "ERROR")

            logger.log(f"Errors found: {len(all_errors)} (console errors: {len(analysis['console_errors'])})")
            logger.log(f"Warnings found: {len(analysis['warnings'])}")

            if all_errors:
                status = "FAIL"
                details.append(f"Errors: {len(all_errors)}")

                non_console_errors = [line for line in analysis["errors"] if line not in analysis["console_errors"]]
                if non_console_errors:
                    limit = min(5, len(non_console_errors))
                    for i in range(limit):
                        logger.log(f"  ERROR: {non_console_errors[i]}", "ERROR")
                    if len(non_console_errors) > 5:
                        logger.log(f"  ... and {len(non_console_errors) - 5} more errors", "ERROR")
            elif not analysis["plugin_loaded"]:
                status = "FAIL"
                details.append("Plugin not loaded")
                logger.log("Plugin was not loaded", "ERROR")
            elif not analysis["plugin_enabled"]:
                status = "WARN"
                details.append("Plugin loaded but not enabled")
                logger.log("Plugin loaded but not enabled", "WARN")
            else:
                status = "SUCCESS"
                details.append("Plugin loaded and enabled")
                logger.log("Plugin loaded and enabled successfully", "SUCCESS")

            if analysis["warnings"]:
                details.append(f"Warnings: {len(analysis['warnings'])}")
                logger.log(f"Found {len(analysis['warnings'])} warnings", "WARN")

        except Exception as exc:
            status = "ERROR"
            details.append(f"Test error: {exc}")
            logger.log(f"ERROR: {exc}", "ERROR")

        results.append({"version": version, "status": status, "details": details})
        logger.log(f"[{version}] {status} - {'; '.join(details)}")
        logger.log("")

    logger.log("===============================================")
    logger.log("FINAL RESULTS")
    logger.log("===============================================")

    for result in results:
        logger.log(f"[{result['version']}] {result['status']} - {'; '.join(result['details'])}")

    success_count = len([r for r in results if r["status"] == "SUCCESS"])
    warn_count = len([r for r in results if r["status"] == "WARN"])
    fail_count = len([r for r in results if r["status"] == "FAIL"])
    error_count = len([r for r in results if r["status"] == "ERROR"])

    logger.log("===============================================")
    logger.log(f"Summary: {success_count} SUCCESS, {warn_count} WARN, {fail_count} FAIL, {error_count} ERROR")
    logger.log(f"Log file: {log_file}")
    logger.log("===============================================")

    if fail_count > 0 or error_count > 0:
        return 1
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] [ERROR] CRITICAL ERROR - Script failed")
        print(f"[{timestamp}] [ERROR] {exc}")
        sys.exit(1)
