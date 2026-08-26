{
  description = "Hablock — playful offline habit-gated app blocker (Android, Material 3 Expressive)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "36" "35" ];
        buildToolsVersions = [ "36.0.0" ];
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
        includeNDK = false;
      };
      sdk = android.androidsdk;
      sdkRoot = "${sdk}/libexec/android-sdk";
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          sdk
          pkgs.jdk17
          pkgs.gradle_8
          pkgs.scrcpy
          (pkgs.python3.withPackages (ps: [ ps.fonttools ]))
        ];
        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = pkgs.jdk17.home;
        # NixOS: the Maven-downloaded aapt2 binary cannot run here; force the SDK one
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/36.0.0/aapt2";
        shellHook = ''
          export PATH="${sdkRoot}/platform-tools:${sdkRoot}/emulator:$PATH"
        '';
      };
    };
}
