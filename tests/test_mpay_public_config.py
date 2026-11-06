import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "mpay-public-config.py"
SPEC = importlib.util.spec_from_file_location("mpay_public_config", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise ImportError(f"Cannot load mPay public URL renderer from {SCRIPT}")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class MpayPublicConfigTests(unittest.TestCase):
    def test_derives_all_public_hosts_from_one_root(self):
        values = MODULE.derive("https://quantaedge.in")
        self.assertEqual(values["MPAY_WEB_PUBLIC_URL"], "https://mpay.quantaedge.in")
        self.assertEqual(values["MPAY_API_PUBLIC_URL"], "https://api-mpay.quantaedge.in")
        self.assertEqual(values["MPAY_TURN_HOST"], "turn-mpay.quantaedge.in")
        self.assertEqual(values["MPAY_WEB_API_BASE_URL"], values["MPAY_API_BASE_URL"])
        self.assertEqual(values["MPAY_CORS_ALLOWED_ORIGINS"], values["MPAY_WEB_PUBLIC_URL"])
        self.assertIn("turn:turn-mpay.quantaedge.in:3478?transport=udp", values["MPAY_CALL_ICE_SERVERS"])

    def test_domain_change_percolates_to_every_derived_origin(self):
        values = MODULE.derive("https://example.org")
        self.assertEqual(values["MPAY_WEB_PUBLIC_URL"], "https://mpay.example.org")
        self.assertEqual(values["MPAY_API_PUBLIC_URL"], "https://api-mpay.example.org")
        self.assertEqual(values["MPAY_CALL_TURN_REALM"], "turn-mpay.example.org")

    def test_rejects_non_root_or_non_https_urls(self):
        invalid = (
            "http://quantaedge.in", "https://quantaedge.in/path",
            "https://user@quantaedge.in", "https://quantaedge.in:8443",
            "https://www.quantaedge.in", "https://quantaedge.in?x=1",
        )
        for value in invalid:
            with self.subTest(value=value), self.assertRaises(MODULE.ConfigError):
                MODULE.derive(value)

    def test_env_file_requires_exactly_one_root_setting(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / ".env.cloudflare"
            path.write_text(
                "MPAY_PUBLIC_ROOT_URL=https://quantaedge.in\nMPAY_DB_PASSWORD=example-only\n",
                encoding="utf-8",
            )
            self.assertEqual(MODULE.read_root(path), "https://quantaedge.in")
            path.write_text(
                "MPAY_PUBLIC_ROOT_URL=https://quantaedge.in\n"
                "MPAY_PUBLIC_ROOT_URL=https://example.org\n",
                encoding="utf-8",
            )
            with self.assertRaises(MODULE.ConfigError):
                MODULE.read_root(path)

    def test_cli_outputs_compose_and_github_env_compatible_values(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--root-url", "https://quantaedge.in", "--format", "github"],
            text=True, capture_output=True, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("MPAY_API_BASE_URL=https://api-mpay.quantaedge.in", result.stdout)
        self.assertIn("MPAY_CORS_ALLOWED_ORIGINS=https://mpay.quantaedge.in", result.stdout)


if __name__ == "__main__":
    unittest.main()
