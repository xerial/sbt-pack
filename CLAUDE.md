# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

sbt-pack is an SBT plugin for creating distributable Scala packages. It bundles applications with all dependencies and generates launch scripts for Unix/Linux and Windows.

## Essential Commands

```bash
# Run unit tests
./sbt test

# Run integration tests (scripted tests)
./sbt scripted

# Run a specific scripted test
./sbt "scripted sbt-pack/multi-module"

# Check code formatting
./sbt scalafmtCheckAll

# Auto-format code
./sbt scalafmtAll

# Compile
./sbt compile

# Clean build
./sbt clean

# Full test suite (run before submitting PRs)
./sbt scalafmtCheckAll test scripted
```

## Code Architecture

### Core Components

1. **src/main/scala/xerial/sbt/pack/PackPlugin.scala** - Main plugin implementation
   - Entry point for all plugin functionality
   - Defines settings, tasks, and configurations
   - Handles dependency resolution and JAR copying

2. **src/main/scala/xerial/sbt/pack/LaunchScript.scala** - Script generation
   - Creates Unix/Linux shell scripts and Windows batch files
   - Generates Makefile for system-wide installation

3. **src/main/scala/xerial/sbt/pack/PackArchive.scala** - Archive creation
   - Creates tar.gz, tar.bz2, tar.xz, and zip archives

4. **src/main/twirl/xerial/sbt/pack/** - Twirl templates
   - Templates for generating launch scripts
   - `launch.scala.txt` - Unix/Linux script template
   - `launch-bat.scala.txt` - Windows batch file template
   - `Makefile.scala.txt` - Installation Makefile template

### Key Plugin Settings

- `packMain` - Map of program names to main classes
- `packJvmOpts` - JVM options per program
- `packExcludeJars` - Regex patterns for excluding JARs
- `packJarNameConvention` - JAR naming strategy
- `packTargetDir` - Output directory (default: target/pack)

### Testing Structure

- **Unit tests**: `src/test/scala/` - Uses Airspec framework
- **Integration tests**: `src/sbt-test/sbt-pack/` - Each subdirectory is a test scenario
  - Test projects simulate real usage scenarios
  - Each test has its own build.sbt and expected outputs

## Development Workflow

1. Code changes must pass `scalafmtCheckAll`
2. All tests must pass: `./sbt test scripted`
3. CI automatically runs tests on PRs affecting `.scala`, `.java`, or `.sbt` files
4. Integration tests use scripted framework - modify test projects in `src/sbt-test/` to test new features

## Important Implementation Details

- The plugin works by:
  1. Collecting all runtime dependencies
  2. Copying JAR files to `target/pack/lib`
  3. Generating launch scripts that set up classpath
  4. Optionally creating distributable archives

- Version conflict resolution uses custom `VersionString` comparison
- Supports multi-module projects with different main classes
- Handles both application and library packaging scenarios
- Special Docker-friendly features for container deployment