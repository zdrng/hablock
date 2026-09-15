{
  description = "Hablock Android development and emulator environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  # Unstable dropped Intel macOS; keep it on the supported stable Darwin branch.
  inputs.nixpkgsDarwin.url = "github:NixOS/nixpkgs/nixpkgs-26.05-darwin";

  outputs = { nixpkgs, nixpkgsDarwin, ... }:
    let
      # Google provides accelerated emulator hosts for these platforms.
      systems = [ "x86_64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSystem = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forEachSystem (system:
        let
          packageSet = if system == "x86_64-darwin" then nixpkgsDarwin else nixpkgs;
          pkgs = import packageSet {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };
          abi = if system == "aarch64-darwin" then "arm64-v8a" else "x86_64";
          android = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ "36" ];
            buildToolsVersions = [ "36.0.0" ];
            toolsVersion = null;
            includeEmulator = true;
            includeSystemImages = true;
            systemImageTypes = [ "google_apis" ];
            abiVersions = [ abi ];
            includeNDK = false;
            includeCmake = false;
          };
          sdk = android.androidsdk;
          sdkRoot = "${sdk}/libexec/android-sdk";
        in
        {
          default = pkgs.mkShell {
            packages = [
              sdk
              pkgs.gnumake
              pkgs.jdk17
              pkgs.gradle_8
              pkgs.scrcpy
              pkgs.shellcheck
              (pkgs.python3.withPackages (ps: [ ps.fonttools ]))
            ];
            ANDROID_HOME = sdkRoot;
            ANDROID_SDK_ROOT = sdkRoot;
            JAVA_HOME = pkgs.jdk17.home;
            HABLOCK_EMULATOR_ABI = abi;
            HABLOCK_EMULATOR_API = "36";
            # Maven's Linux aapt2 cannot run directly on NixOS. macOS uses AGP's binary.
            GRADLE_OPTS = pkgs.lib.optionalString pkgs.stdenv.hostPlatform.isLinux
              "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/36.0.0/aapt2";
            shellHook = ''
              export PATH="${sdkRoot}/platform-tools:${sdkRoot}/emulator:$PATH"
              echo "Hablock: scripts/emulator.sh up [--window] builds, boots and launches the app."
            '';
          };
        });
    };
}
