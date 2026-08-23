<div align="center">

# 🔍 CompareKit

**Material UI file, folder, zip, and APK comparison app for Android**

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![UI](https://img.shields.io/badge/UI-Material%203-6750A4)
![Made by](https://img.shields.io/badge/made%20by-BlazeFTL-blue)

</div>

---

## Features

- Compare files, folders, zip archives, or **APKs** — side-by-side or unified diff view
- **APK, DEX & Smali comparison** — disassembles every DEX into virtual Smali automatically, no manual decompiling needed
- **Multidex aware** — inspect a combined multi-DEX tree or drill into a single DEX
- **Bytecode normalization rules** to cut through compiler noise:
  - Ignore Debug Info
  - Ignore Compilation Optimization
  - Ignore NOP Instructions
  - Ignore Register Count
  - Ignore Field Default Values
- Categorized results: All · Modified · Added · Deleted · Moved · Unchanged
- Package tree or flat file list view
- Line-level diff highlighting with change block navigation ("Go to Line")
- Search within diffs, filter files by extension or pattern
- Split/unified view, line wrapping, adjustable text size & line height, zoom in/out
- Export diffs as:
  - **Unified Diff** (`.diff`) — compatible with git, IDEs, code editors
  - **Text Report** (`.txt`) — readable stock-vs-modified line report
  - **Folder Archive** (`.zip`) — changed files only, original directory structure preserved
- 7 built-in themes — Forest Green, Ocean Blue, Teal Jade, Royal Purple, Sunset Amber, Crimson Rose, Charcoal Slate

---

## Screenshots
<img width="702" height="1560" alt="Screenshot_20260823-161157_Spark Launcher" src="https://github.com/user-attachments/assets/45e0cf6d-4dc5-4372-92be-f07ad8269193" />
<img width="702" height="1560" alt="Screenshot_20260823-161200_Spark Launcher" src="https://github.com/user-attachments/assets/3c0f55c0-bef9-403a-90bd-d9ba2e602a1c" />
<img width="702" height="1560" alt="Screenshot_20260823-161204_Spark Launcher" src="https://github.com/user-attachments/assets/fbf43727-d545-4f7e-8066-776285b8455a" />
<img width="702" height="1560" alt="Screenshot_20260823-161227_Spark Launcher" src="https://github.com/user-attachments/assets/f326d376-bb6e-4769-bf9f-b85f061fe9ab" />
<img width="702" height="1560" alt="Screenshot_20260823-161224_Spark Launcher" src="https://github.com/user-attachments/assets/236a20c2-3a43-40c5-a8a7-767fccec3faf" />
<img width="702" height="1560" alt="Screenshot_20260823-161221_Spark Launcher" src="https://github.com/user-attachments/assets/a8fee9df-9de6-4039-b9e1-2f4d4067b5cc" />
<img width="702" height="1560" alt="Screenshot_20260823-161252_Spark Launcher" src="https://github.com/user-attachments/assets/8205662b-faf5-454e-91b4-fbe7dd04647d" />
<img width="702" height="1560" alt="Screenshot_20260823-161241_Spark Launcher" src="https://github.com/user-attachments/assets/e84a0f0e-bd58-4c69-a70a-e8d642ec2122" />
<img width="702" height="1560" alt="Screenshot_20260823-161235_Spark Launcher" src="https://github.com/user-attachments/assets/aa66a9fa-66c8-4138-86f1-0ebcffdc41bd" />
<img width="702" height="1560" alt="Screenshot_20260823-161254_Spark Launcher" src="https://github.com/user-attachments/assets/be6555a3-b679-4993-98f4-619738493b6c" />
<img width="702" height="1560" alt="Screenshot_20260823-161258_Spark Launcher" src="https://github.com/user-attachments/assets/4d5f98b3-d240-4e89-9f55-efd32a7d9f47" />


| Pick Items | Diff View | File List |
|:---:|:---:|:---:|
| Select original/modified files, folders, or APKs | Line-by-line diff with change blocks | Modified/Added/Deleted/Moved breakdown |

<!-- Add new screenshots here: APK bytecode options dialog, DEX package tree, Smali diff viewer, export format picker -->

---

## Usage

1. Open CompareKit
2. Tap **Pick Original File** and select your source — file, folder, zip, or APK
3. Tap **Pick Modified File** and select the changed version
4. If comparing APKs, configure bytecode filtering rules and tap **Start Comparison**
5. Browse results by category (All / Modified / Added / Deleted / Moved / Unchanged), as a package tree or flat list
6. Tap any file to view the detailed diff — for APKs, DEX files open as disassembled Smali
7. Use the menu (⋮) to search, switch views, zoom, adjust filters, or export

## Supported Inputs

- Individual files
- Folders (recursive)
- Zip archives (`.zip`)
- APKs (`.apk`) — including multidex, compared down to Smali bytecode

## Installation

Download the latest APK from [**Releases**](../../releases).

---

## Author

Built by [**BlazeFTL**](https://github.com/BlazeFTL)
