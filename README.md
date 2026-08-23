<h1 align="center">CompareKit</h1>
<p align="center">
  <b>Diff tool for Android — Files, Folders, ZIPs & APKs</b><br>
  <sub>Compare anything. Smali-level APK analysis included.</sub>
</p>

<p align="center">
  <a href="../../releases"><img src="https://img.shields.io/github/v/release/BlazeFTL/CompareKit?style=flat-square&color=blue" alt="Release"></a>
  <a href="#"><img src="https://img.shields.io/badge/platform-Android-green?style=flat-square&logo=android" alt="Platform"></a>
  <a href="../../LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue?style=flat-square" alt="License"></a>
</p>

---

## Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/a8fee9df-9de6-4039-b9e1-2f4d4067b5cc" width="200" alt="Pick Items"/>
  <img src="https://github.com/user-attachments/assets/8205662b-faf5-454e-91b4-fbe7dd04647d" width="200" alt="Diff View"/>
  <img src="https://github.com/user-attachments/assets/e84a0f0e-bd58-4c69-a70a-e8d642ec2122" width="200" alt="File List"/>
  <img src="https://github.com/user-attachments/assets/aa66a9fa-66c8-4138-86f1-0ebcffdc41bd" width="200" alt="Bytecode Options"/>
  <img src="https://github.com/user-attachments/assets/be6555a3-b679-4993-98f4-619738493b6c" width="200" alt="DEX Tree"/>
  <img src="https://github.com/user-attachments/assets/4d5f98b3-d240-4e89-9f55-efd32a7d9f47" width="200" alt="Smali Diff"/>
</p>

---

## Features

| Feature | Description |
|:---|:---|
| **Multi-format Support** | Compare individual files, folders, ZIP archives, and APKs |
| **APK Deep Dive** | Disassemble DEX to Smali bytecode with multidex support |
| **Smart Filtering** | Configure bytecode filtering rules before comparison |
| **Diff Viewer** | Line-by-line change blocks with syntax highlighting |
| **File Tree** | Browse results as package tree or flat list |
| **Export** | Save results in multiple formats via the options menu |

---

## Usage

1. Launch CompareKit
2. Tap **Pick Original File** — select a file, folder, ZIP, or APK
3. Tap **Pick Modified File** — select the changed version
4. *(APK only)* Configure bytecode filters, then tap **Start Comparison**
5. Browse results by category: **All / Modified / Added / Deleted / Moved / Unchanged**
6. Tap any file to open its diff — DEX files render as disassembled Smali
7. Tap **⋮** to search, switch views, zoom, adjust filters, or export

---

## Supported Inputs

- Single files
- Folders (recursive)
- ZIP archives (`.zip`)
- Android packages (`.apk`) — multidex, Smali-level comparison

---

## Download

Get the latest APK from [**Releases**](../../releases).

---

<p align="center">
  Built by <a href="https://github.com/BlazeFTL"><b>BlazeFTL</b></a>
</p>
