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
    def test_derives_web_api_and_cors_from_one_root(self):
        values = MODULE.derive("https://quantaedge.in")
        self.assertEqual(values["MPAY_WEB_PUBLIC_URL"], "https://mpay.quantaedge.in")
        self.assertEqual(values["MPAY_API_PUBLIC_URL"], "https://api-mpay.quantaedge.in")
        self.assertEqual(values["MPAY_TURN_HOST"], "turn-mpay.quantaedge.in")
        self.assertEqual(values["MPAY_WEB_API_BASE_URL"], values["MPAY_API_BASE_URL"])
        self.assertEqual(values["MPAY_CORS_ALLOWED_ORIGINS"], values["MPAY_WEB_PUBLIC_URL"])

    def test_turn_is_not_advertised_until_explicitly_enabled(self):
        values = MODULE.derive("https://quantaedge.in")
        self.assertEqual(values["MPAY_CALL_ICE_SERVERS"], "stun:stun.l.google.com:19302")
        self.assertNotIn("turn:", values["MPAY_CALL_ICE_SERVERS"])

    def test_turn_host_is_derived_when_external_turn_is_enabled(self):
        values = MODULE.derive("https://quantaedge.in", turn_enabled=True)
        self.assertEqual(values["MPAY_CALL_TURN_REALM"], "turn-mpay.quantaedge.in")
        self.assertIn("turn:turn-mpay.quantaedge.in:3478?transport=udp", values["MPAY_CALL_ICE_SERVERS"])
        self.assertIn("turn:turn-mpay.quantaedge.in:3478?transport=tcp", values["MPAY_CALL_ICE_SERVERS"])

    def test_domain_change_percolates_to_every_derived_origin(self):
        values = MODULE.derive("https://example.org", turn_enabled=True)
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

    def test_config_file_requires_one_root_and_valid_turn_flag(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "public-hosts.env"
            path.write_text(
                "MPAY_PUBLIC_ROOT_URL=https://quantaedge.in\nMPAY_TURN_ENABLED=false\n",
                encoding="utf-8",
            )
            self.assertEqual(MODULE.read_settings(path), ("https://quantaedge.in", False))
            path.write_text(
                "MPAY_PUBLIC_ROOT_URL=https://quantaedge.in\nMPAY_TURN_ENABLED=true\n",
                encoding="utf-8",
            )
            self.assertEqual(MODULE.read_settings(path), ("https://quantaedge.in", True))
            path.write_text(
                "MPAY_PUBLIC_ROOT_URL=https://quantaedge.in\n"
                "MPAY_PUBLIC_ROOT_URL=https://example.org\nMPAY_TURN_ENABLED=maybe\n",
                encoding="utf-8",
            )
            with self.assertRaises(MODULE.ConfigError):
                MODULE.read_settings(path)

    def test_cli_outputs_compose_and_github_env_compatible_values(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--env-file", str(ROOT / "config/public-hosts.env"), "--format", "github"],
            text=True, capture_output=True, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("MPAY_API_BASE_URL=https://api-mpay.quantaedge.in", result.stdout)
        self.assertIn("MPAY_CORS_ALLOWED_ORIGINS=https://mpay.quantaedge.in", result.stdout)
        self.assertIn("MPAY_CALL_ICE_SERVERS=stun:stun.l.google.com:19302", result.stdout)


if __name__ == "__main__":
    unittest.main()
