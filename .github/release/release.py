#!/usr/bin/env python3
"""Entry point of .github/workflows/release.yml: one subcommand per workflow step."""

import argparse
import datetime
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

import checks
import consumers
import contract
import index
import layout
import manifest as manifest_module
import sbom
import sign
import sources
import upload
import verify
from plan import PlanError, checkout_tags, load_config, plan_for_tag, plan_from_json
from s3 import R2Bucket

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[1]
PUBLIC_KEY = HERE / "countly-sdk-signing.asc"
RELEASE_KEY_FINGERPRINT = "FC8DB0DE234A273BA45E562FB8C83A079A5BBD0C"
TARGETS = ["production", "test"]


def summary(lines):
    """Prints lines and appends them to the job summary when running in GitHub Actions."""
    for line in lines:
        print(line)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")


def outputs(values):
    """Writes step outputs when running in GitHub Actions."""
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as handle:
            for key, value in values.items():
                handle.write(f"{key}={value}\n")


def finish(problems, title):
    """Reports problems as error annotations and exits with 1 when there are any; reports success otherwise."""
    if problems:
        for problem in problems:
            print(f"::error::{problem}")
        summary([f"### {title}: failed"] + [f"- {problem}" for problem in problems])
        sys.exit(1)
    summary([f"### {title}: passed"])


def read_plan(path):
    """Loads config.json and the plan written by the plan step."""
    config = load_config()
    return config, plan_from_json(config, json.loads(Path(path).read_text(encoding="utf-8")))


def bucket_from_env():
    """The R2 bucket named by the R2_BUCKET and R2_ACCOUNT_ID variables."""
    return R2Bucket(os.environ["R2_BUCKET"], os.environ["R2_ACCOUNT_ID"])


def staged_file(staging, artifact, version, suffix):
    """Path of one staged file of an artifact version, e.g. suffix '.aar' or '-cyclonedx.json'."""
    return Path(staging) / artifact.folder(version) / f"{artifact.base_name(version)}{suffix}"


def cmd_plan(args):
    """Maps the tag to a plan, writes plan.json and the step outputs, and checks the GitHub release type."""
    config = load_config()
    try:
        plan = plan_for_tag(config, args.tag)
    except PlanError as error:
        finish([str(error)], "Tag")
        return
    Path(args.out).write_bytes(json.dumps(plan.to_json(), indent=2).encode("utf-8"))
    outputs({
        "environment": "maven-release" if args.target == "production" else "maven-test",
        "check_tasks": " ".join(f"{module}:check" for module in plan.modules),
        "publish_tasks": " ".join(f"{module}:publishAllPublicationsToReleaseStagingRepository {module}:cyclonedxDirectBom" for module in plan.modules),
        "published_modules": config["publishedModules"],
    })
    state = "listed" if plan.listed else "release candidate, not listed"
    lines = [f"### Plan for {plan.tag}", f"- version {plan.version} ({state})", f"- branch {plan.branch}", f"- target {args.target}"]
    if args.commit:
        lines.append(f"- commit {args.commit}")
    summary(lines + [f"- {artifact.coordinates}" for artifact in plan.artifacts])
    problems = []
    if args.target == "production":
        if args.prerelease not in ("true", "false"):
            problems.append("a production release needs a published GitHub release for this tag")
        elif (args.prerelease == "true") != plan.prerelease:
            problems.append("a tag ending in -rcN must be published as a GitHub pre-release, and a pre-release needs such a tag")
    finish(problems, "GitHub release")


def cmd_checkout_tags(args):
    """Writes the tags the checkout would be released as from --branch, as the JSON list the release checks run on."""
    try:
        tags = checkout_tags(load_config(), REPO_ROOT, args.branch)
    except PlanError as error:
        finish([str(error)], "Checkout tags")
        return
    outputs({"tags": json.dumps(tags)})
    summary([f"### Tags {args.branch} would release"] + [f"- {tag}" for tag in tags or ["none"]])


def cmd_check_branch(args):
    """The tag commit must be on the plan's release branch."""
    _, plan = read_plan(args.plan)
    finish(checks.branch_problems(args.commit, plan.branch, REPO_ROOT), "Release branch")


def cmd_check_sources(args):
    """Version numbers and changelog headings must match the tag."""
    config, plan = read_plan(args.plan)
    finish(sources.check_version_sources(REPO_ROOT, plan, config), "Version numbers")


def cmd_check_absent(args):
    """The version must exist neither in the target repository nor, for production, on Maven Central."""
    config, plan = read_plan(args.plan)
    base = config["publicBaseUrl"][args.target]
    problems = []
    for artifact in plan.artifacts:
        pom = f"{artifact.folder(plan.version)}/{artifact.base_name(plan.version)}.pom"
        if verify.fetch(f"{base}{pom}?nocache={args.nocache}") is not None:
            problems.append(f"{artifact.coordinates}:{plan.version} is already on {base}")
        if args.target == "production" and verify.fetch(f"{config['centralBaseUrl']}{pom}") is not None:
            problems.append(f"{artifact.coordinates}:{plan.version} is already on Maven Central")
    finish(problems, "Not published yet")


def cmd_check_required(args):
    """The required status checks must have succeeded on the tag commit."""
    config = load_config()
    runs = json.loads(Path(args.check_runs).read_text(encoding="utf-8"))
    finish([f"required check '{name}' did not succeed on the tag commit" for name in checks.missing_checks(runs, config["requiredChecks"])], "Required checks")


def cmd_stage(args):
    """Removes Gradle's indexes, places the SBOMs, checks the file set and writes the manifest."""
    config, plan = read_plan(args.plan)
    staging = Path(args.staging)
    layout.remove_index_files(staging)
    timestamp = subprocess.run(["git", "-C", str(REPO_ROOT), "show", "-s", "--format=%cI", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    problems = []
    lines = []
    for artifact in plan.artifacts:
        if not artifact.sbom:
            continue
        fragment = None
        if artifact.sbom_fragment:
            fragment = json.loads((REPO_ROOT / artifact.sbom_fragment).read_text(encoding="utf-8"))
            problems += sbom.check_fragment(fragment, sbom.git_runner(REPO_ROOT))
        source = REPO_ROOT / artifact.module_dir / "build/reports/cyclonedx-direct/bom.json"
        if not source.is_file():
            problems.append(f"no SBOM at {artifact.module_dir}/build/reports/cyclonedx-direct/bom.json")
            continue
        placed = sbom.place_sbom(source, staging, artifact, plan.version, timestamp, config["publicBaseUrl"][args.target], fragment)
        lines.append(f"- SBOM of {artifact.coordinates}: {len(json.loads(placed.read_bytes()).get('components', []))} components")
    problems += layout.check_staging(staging, plan)
    if not problems:
        data = manifest_module.build_manifest(staging, plan.tag, args.commit)
        Path(args.manifest).write_bytes(json.dumps(data, indent=2).encode("utf-8"))
        lines += [f"- {len(data['files'])} files staged", "", "<details><summary>Staged files</summary>", ""]
        lines += [f"    {entry['path']}" for entry in data["files"]] + ["", "</details>"]
    summary(lines)
    finish(problems, "Staging")


def cmd_validate_sbom(args):
    """Validates every staged SBOM against the CycloneDX 1.6 schema with the CycloneDX CLI."""
    _, plan = read_plan(args.plan)
    env = dict(os.environ, DOTNET_SYSTEM_GLOBALIZATION_INVARIANT="1")
    problems = []
    for artifact in plan.artifacts:
        if not artifact.sbom:
            continue
        path = staged_file(args.staging, artifact, plan.version, "-cyclonedx.json")
        result = subprocess.run([args.cli, "validate", "--input-file", str(path), "--input-format", "json", "--input-version", "v1_6", "--fail-on-errors"], env=env)
        if result.returncode != 0:
            problems.append(f"{path.name} is not a valid CycloneDX 1.6 document")
    finish(problems, "SBOM schema")


def cmd_contract(args):
    """Compares each staged artifact with its contract file and writes the comparison table; --capture prints the actual contracts instead."""
    _, plan = read_plan(args.plan)
    problems = []
    lines = []
    for artifact in plan.artifacts:
        if not artifact.contract:
            continue
        actual = contract.actual_contract(args.staging, artifact, plan.version)
        if args.capture:
            print(json.dumps(actual, indent=2))
            continue
        expected = json.loads((REPO_ROOT / artifact.contract).read_text(encoding="utf-8"))
        found = contract.compare(expected, actual) + contract.module_dependency_problems(args.staging, artifact, plan.version)
        if artifact.native_libraries:
            aar = staged_file(args.staging, artifact, plan.version, ".aar").read_bytes()
            found += contract.committed_library_problems(aar, REPO_ROOT, artifact.native_libraries)
        problems += [f"{artifact.coordinates}: {problem}" for problem in found]
        lines += [f"#### {artifact.coordinates}", "", "| Property | Contract | Built |", "|---|---|---|"]
        lines += [f"| {name} | {wanted} | {built} |" for name, wanted, built in contract.summary_rows(expected, actual)] + [""]
    if not args.capture:
        summary(lines)
        finish(problems, "Contract")


def cmd_verify_manifest(args):
    """The downloaded staging folder must be exactly what the build job produced."""
    data = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    finish(manifest_module.verify_manifest(args.staging, data), "Manifest")


def cmd_sign(args):
    """Signs every staged file with the key of the target held in GNUPGHOME and checks every signature. Production
    must use the release key; a dry run must use its own key. The public key the signatures verify against is written
    to --public-key-out for the verify job: the committed release key, or the dry-run key exported from the keyring."""
    homedir = os.environ["GNUPGHOME"]
    production = args.target == "production"
    try:
        fingerprint = sign.signing_fingerprint(homedir, RELEASE_KEY_FINGERPRINT, production)
    except sign.SigningError as error:
        finish([str(error)], "Signing key")
    if production:
        shutil.copyfile(PUBLIC_KEY, args.public_key_out)
    else:
        sign.export_public_key(homedir, fingerprint, args.public_key_out)
    signed = sign.sign_staging(args.staging, homedir, os.environ["SIGNING_KEY_PASSPHRASE"], fingerprint)
    finish(verify.verify_signatures(args.staging, Path(args.public_key_out)), f"Signing ({len(signed)} files with {fingerprint})")


def cmd_upload(args):
    """Uploads the signed staging folder create-only."""
    _, plan = read_plan(args.plan)
    try:
        upload.upload_release(bucket_from_env(), args.staging, plan)
    except upload.UploadError as error:
        finish([str(error), "this version number is burned; release the next patch version"], "Upload")
    finish([], "Upload")


def cmd_index(args):
    """Rewrites maven-metadata.xml of the plan's artifacts, or of every artifact of this repository with --all."""
    config = load_config()
    if args.all:
        targets = [(spec["group"], spec["artifact"]) for spec in config["artifacts"].values()]
    else:
        _, plan = read_plan(args.plan)
        targets = [(artifact.group, artifact.artifact) for artifact in plan.artifacts]
    bucket = bucket_from_env()
    now = datetime.datetime.now(datetime.timezone.utc)
    for group, name in targets:
        index.rewrite_index(bucket, group, name, now)
    finish([], "Version index")


def cmd_verify_public(args):
    """Downloads the release from the public address and checks files, index and signatures."""
    config, plan = read_plan(args.plan)
    data = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    problems = verify.verify_public(data, plan, config["publicBaseUrl"][args.target], args.nocache, args.work)
    if not problems:
        problems = verify.verify_signatures(args.work, Path(args.public_key))
    finish(problems, "Public files")


def cmd_consumer(args):
    """Builds the consumer app on every AGP/Gradle pair against the staging folder, used as a local repository, so a
    release that a clean app cannot build with stops before the approval."""
    config, plan = read_plan(args.plan)
    repository = Path(args.staging).resolve().as_uri()
    gradlew = str(REPO_ROOT / "gradlew")
    checkout_agp = consumers.project_agp((REPO_ROOT / "build.gradle").read_text(encoding="utf-8"))
    problems = []
    for artifact in plan.artifacts:
        if not artifact.consumer_probe:
            continue
        for pair in config["consumerPairs"]:
            if subprocess.run(consumers.android_command(pair, gradlew, repository, artifact, plan.version, checkout_agp), cwd=REPO_ROOT).returncode != 0:
                problems.append(f"{pair['name']}: a clean app could not build with {artifact.coordinates}:{plan.version}")
    finish(problems, "Consumer builds")


def main(argv=None):
    """Parses the subcommand and runs it."""
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    command = commands.add_parser("plan")
    command.add_argument("--tag", required=True)
    command.add_argument("--target", choices=TARGETS, required=True)
    command.add_argument("--prerelease", default="unknown")
    command.add_argument("--commit", default="")
    command.add_argument("--out", required=True)
    command.set_defaults(func=cmd_plan)

    command = commands.add_parser("checkout-tags")
    command.add_argument("--branch", required=True)
    command.set_defaults(func=cmd_checkout_tags)

    command = commands.add_parser("check-branch")
    command.add_argument("--plan", required=True)
    command.add_argument("--commit", required=True)
    command.set_defaults(func=cmd_check_branch)

    command = commands.add_parser("check-sources")
    command.add_argument("--plan", required=True)
    command.set_defaults(func=cmd_check_sources)

    command = commands.add_parser("check-absent")
    command.add_argument("--plan", required=True)
    command.add_argument("--target", choices=TARGETS, required=True)
    command.add_argument("--nocache", required=True)
    command.set_defaults(func=cmd_check_absent)

    command = commands.add_parser("check-required")
    command.add_argument("--check-runs", required=True)
    command.set_defaults(func=cmd_check_required)

    command = commands.add_parser("stage")
    command.add_argument("--plan", required=True)
    command.add_argument("--staging", required=True)
    command.add_argument("--commit", required=True)
    command.add_argument("--target", choices=TARGETS, required=True)
    command.add_argument("--manifest", required=True)
    command.set_defaults(func=cmd_stage)

    command = commands.add_parser("validate-sbom")
    command.add_argument("--plan", required=True)
    command.add_argument("--staging", required=True)
    command.add_argument("--cli", required=True)
    command.set_defaults(func=cmd_validate_sbom)

    command = commands.add_parser("contract")
    command.add_argument("--plan", required=True)
    command.add_argument("--staging", required=True)
    command.add_argument("--capture", action="store_true")
    command.set_defaults(func=cmd_contract)

    command = commands.add_parser("verify-manifest")
    command.add_argument("--manifest", required=True)
    command.add_argument("--staging", required=True)
    command.set_defaults(func=cmd_verify_manifest)

    command = commands.add_parser("sign")
    command.add_argument("--staging", required=True)
    command.add_argument("--target", choices=TARGETS, required=True)
    command.add_argument("--public-key-out", required=True)
    command.set_defaults(func=cmd_sign)

    command = commands.add_parser("upload")
    command.add_argument("--plan", required=True)
    command.add_argument("--staging", required=True)
    command.set_defaults(func=cmd_upload)

    command = commands.add_parser("index")
    command.add_argument("--plan")
    command.add_argument("--all", action="store_true")
    command.set_defaults(func=cmd_index)

    command = commands.add_parser("verify-public")
    command.add_argument("--plan", required=True)
    command.add_argument("--manifest", required=True)
    command.add_argument("--target", choices=TARGETS, required=True)
    command.add_argument("--nocache", required=True)
    command.add_argument("--work", required=True)
    command.add_argument("--public-key", default=str(PUBLIC_KEY))
    command.set_defaults(func=cmd_verify_public)

    command = commands.add_parser("consumer")
    command.add_argument("--plan", required=True)
    command.add_argument("--staging", required=True)
    command.set_defaults(func=cmd_consumer)

    args = parser.parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    main()
