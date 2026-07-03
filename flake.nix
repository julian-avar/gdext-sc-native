{
  description = "Scala Native bindings for Godot Engine via GDExtension";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        llvm = pkgs.llvmPackages;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.jdk21
            pkgs.mill
            llvm.clang
            llvm.llvm
            llvm.lld
            llvm.libunwind
            pkgs.zlib
            pkgs.boehmgc
            pkgs.git
            pkgs.mesa
            # pkgs.godot_4_3
            # pkgs.godot_4_4
            # pkgs.godot_4_5
            # pkgs.godot_4_6
            pkgs.godot_4_7
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.jdk21}"
            export CC="${llvm.clang}/bin/clang"
            export CXX="${llvm.clang}/bin/clang++"
            export LLVM_BIN="${llvm.llvm}/bin"
            # Coursier downloads Zulu JDK binaries that need libz on NixOS
            export LD_LIBRARY_PATH="${pkgs.zlib}/lib:${pkgs.boehmgc}/lib:${llvm.libunwind}/lib:${pkgs.mesa}/lib''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            # Software rendering fallback — remove if a real GPU is available
            export LIBGL_ALWAYS_SOFTWARE=1
            export MESA_GL_VERSION_OVERRIDE=4.5COMPAT
          '';
        };
      }
    );
}
