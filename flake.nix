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
            pkgs.godot_4_5
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.jdk21}"
            export CC="${llvm.clang}/bin/clang"
            export CXX="${llvm.clang}/bin/clang++"
            export LLVM_BIN="${llvm.llvm}/bin"
            # Coursier downloads Zulu JDK binaries that need libz on NixOS
            export LD_LIBRARY_PATH="${pkgs.zlib}/lib:${pkgs.boehmgc}/lib:${llvm.libunwind}/lib''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
          '';
        };
      }
    );
}
