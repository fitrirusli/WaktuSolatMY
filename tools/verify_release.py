"""Fail a release if its actual APK, signatures or packaged files violate the security baseline."""
from pathlib import Path
import hashlib
import json
import os
import re
import ssl
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / '.tools/android-sdk/build-tools/35.0.0'
JAVA = Path(os.environ['JAVA_HOME']) / 'bin/java.exe'
SIGNER = [str(JAVA), '-jar', str(SDK / 'lib/apksigner.jar')]
LEGACY_SHA256 = '5776fd0edfefd3e439d316c9fd0d7c0035d01a8992bbd76eb1c07d29c1500ffd'


def run(args):
    result = subprocess.run([str(arg) for arg in args], capture_output=True, text=True, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def require(value, message):
    if not value:
        raise RuntimeError(message)


def signer_digest(output):
    match = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)', output)
    require(match is not None, 'Missing signer certificate')
    return match.group(1).lower()


def main():
    apk = Path(sys.argv[1]).resolve()
    run([SDK / 'zipalign.exe', '-c', '-P', '16', '4', apk])
    badging = run([SDK / 'aapt.exe', 'dump', 'badging', apk])
    require('application-debuggable' not in badging, 'Debuggable APK cannot be distributed as release')
    require("name='my.waktusolat.app'" in badging, 'Unexpected application ID')
    version = re.search(r"versionName='([^']+)'", badging).group(1)
    manifest = run([SDK / 'aapt.exe', 'dump', 'xmltree', apk, 'AndroidManifest.xml'])
    debug_attribute = re.search(r'A: android:debuggable\([^)]*\)=(.*)', manifest)
    require(debug_attribute is None or debug_attribute.group(1).strip() == '(type 0x12)0x0', 'Debugging must be disabled (absent defaults to false on Android)')
    for attribute in ['allowBackup', 'fullBackupContent', 'usesCleartextTraffic']:
        require(re.search(r'A: android:' + attribute + r'\([^)]*\)=\(type 0x12\)0x0(?:\s|$)', manifest), f'{attribute} must explicitly be false')
    for attribute in ['networkSecurityConfig', 'dataExtractionRules']:
        require('android:' + attribute + '(' in manifest, f'Missing {attribute}')
    permission_output = run([SDK / 'aapt.exe', 'dump', 'permissions', apk])
    permissions = sorted(re.findall(r"uses-permission: name='([^']+)'", permission_output))
    require(set(permissions) == {
        'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE', 'android.permission.RECEIVE_BOOT_COMPLETED'
    }, 'Unexpected app permissions')

    components = []
    current = None
    for line in manifest.splitlines():
        indent = len(line) - len(line.lstrip())
        element = re.search(r'E: (activity|receiver|service|provider) ', line)
        if element:
            current = {'type': element.group(1), 'indent': indent}
            components.append(current)
        elif 'E: ' in line and current is not None and indent <= current['indent']:
            current = None
        elif current is not None and indent == current['indent'] + 2:
            attribute = re.search(r'A: android:(name|exported|permission)\([^)]*\)=(.*)', line)
            if attribute:
                value = attribute.group(2)
                quoted = re.search(r'"([^"]*)"', value)
                current[attribute.group(1)] = quoted.group(1) if quoted else value.strip()
    require(len(components) == 4, 'Unexpected exported/component surface')
    for component in components:
        name = component.get('name', '')
        if component['type'] == 'activity':
            require(name.endswith('.MainActivity'), 'Unexpected exported activity')
        elif component['type'] == 'service':
            require(name.endswith('.SyncJob') and component.get('permission') == 'android.permission.BIND_JOB_SERVICE', 'Service must be system-protected')
        else:
            require(component.get('exported') == '(type 0x12)0x0', 'Receiver/provider must not be exported')
        component.pop('indent')

    certificate_pem = (ROOT / 'signing/fitrirusli-release.pem').read_text(encoding='utf-8')
    certificate_digest = hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate_pem)).hexdigest()
    require(certificate_digest != LEGACY_SHA256, 'Release must use its own key on modern Android')
    all_signatures = run(SIGNER + ['verify', '--verbose', '--print-certs', str(apk)])
    require('Verified using v2 scheme (APK Signature Scheme v2): true' in all_signatures, 'Missing legacy-compatible v2 signature')
    require('Verified using v3 scheme (APK Signature Scheme v3): true' in all_signatures, 'Missing rotated v3 signature')
    old_platform = run(SIGNER + ['verify', '--min-sdk-version', '26', '--max-sdk-version', '27', '--print-certs', str(apk)])
    require(signer_digest(old_platform) == LEGACY_SHA256, 'Android 8 upgrade signature changed')
    modern_platform = run(SIGNER + ['verify', '--min-sdk-version', '28', '--max-sdk-version', '36', '--print-certs', str(apk)])
    require(signer_digest(modern_platform) == certificate_digest, 'Modern Android release signer does not match the protected key')
    lineage_output = run(SIGNER + ['lineage', '--in', str(ROOT / 'signing/rotation.lineage'), '--print-certs'])
    history = re.findall(r'in lineage certificate SHA-256 digest: ([0-9a-f]+)', lineage_output)
    require(history == [LEGACY_SHA256, certificate_digest], 'Upgrade proof must link the original app key to the release key')
    old_capabilities = lineage_output.split('Signer #2 in lineage')[0]
    require('Has installed data capability: true' in old_capabilities and re.search(r'Has rollback capability\s*: false', old_capabilities), 'Upgrade proof must preserve installed data and prohibit rollback to the old key')

    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(not any(name.lower().endswith(('.p12', '.pfx', '.jks', '.keystore', '.dpapi')) for name in names), 'Private signing material found in APK')
        require(not any('jakim-wly01-year' in name or 'SmokeInstrumentation' in name for name in names), 'Test fixture/runner found in APK')
        require([name for name in names if name.startswith('assets/')] == ['assets/zones.json'], 'Unexpected bundled assets')
        xml_trees = [run([SDK / 'aapt.exe', 'dump', 'xmltree', apk, name]) for name in names if name.startswith('res/') and name.endswith('.xml')]
    network_tree = next((tree for tree in xml_trees if 'E: network-security-config ' in tree), '')
    require(network_tree and 'cleartextTrafficPermitted=(type 0x12)0x0' in network_tree, 'Compiled network config must reject cleartext')
    require('src="system"' in network_tree and 'src="user"' not in network_tree and 'debug-overrides' not in network_tree, 'Compiled network config must trust only system CAs')
    extraction_tree = next((tree for tree in xml_trees if 'E: data-extraction-rules ' in tree), '')
    require(extraction_tree and 'E: cloud-backup ' in extraction_tree and 'E: device-transfer ' in extraction_tree, 'Missing compiled backup/transfer exclusions')
    for domain in ['root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref']:
        require(extraction_tree.count('A: domain="' + domain + '"') == 2, f'Backup and transfer must exclude {domain}')
    with apk.open('rb') as stream:
        apk_digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    report = {
        'version': version, 'apk': apk.name, 'sha256': apk_digest,
        'debuggable': False, 'cleartextTraffic': False, 'backups': False,
        'permissions': permissions, 'components': components,
        'releaseCertificateSha256': certificate_digest,
        'legacyCertificateSha256': LEGACY_SHA256,
        'signaturesVerifiedForAndroidApi': '26-36; rotated release key from API 28',
        'privateKeysPackaged': False,
        'installedDataUpgradeProofVerified': True,
        'legacyKeyRollbackAllowed': False,
        'networkCertificateTrust': 'system CAs only; no debug overrides',
        'scope': 'Automated APK/configuration checks; not a penetration-test certification.'
    }
    report_path = apk.with_suffix('.security.json')
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('PASS: non-debuggable release; HTTPS/backup/permissions/components checked; signing-key rotation verified for API 26-36; no private keys packaged.')
    print('Report:', report_path.name)


if __name__ == '__main__':
    main()
