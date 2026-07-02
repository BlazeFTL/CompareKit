# CompareKit

A Material UI file/folder/zip comparison app for Android.

## Features

- Compare two files, folders, or zip archives
- Side-by-side or unified diff view
- Categorized results: All, Modified, Added, Deleted, Unchanged
- Line-level diff highlighting with change block navigation ("Go to Line")
- Search within diffs
- Split view toggle
- Line wrapping toggle
- Zoom in/out
- Filter files by name
- Export diff results as:
  - Standard Unified Diff (`.diff`) — compatible with git, IDEs, code editors
  - Stock vs Modified text report (`.txt`) — readable side-by-side line report
- 9 built-in color themes: Compare Slate, Midnight Indigo, Forest Green, Sunset Amber, Ocean Cyan, Sakura Rose, Nordic Ice, Cosmic Purple, Vintage Sepia

## Screenshots
<img width="702" height="1560" alt="Screenshot_20260702-132651_Spark Launcher" src="https://github.com/user-attachments/assets/4cbce875-bd61-4391-8ad8-48e7a904f568" />
<img width="702" height="1560" alt="Screenshot_20260702-132654_Spark Launcher" src="https://github.com/user-attachments/assets/ab2963c9-1e35-4f75-9233-c8b35b9fc763" />
<img width="702" height="1560" alt="Screenshot_20260702-132556_Spark Launcher" src="https://github.com/user-attachments/assets/3ed67acd-dce0-46c1-add9-1797fb8512bf" />
<img width="702" height="1560" alt="Screenshot_20260702-132628_Spark Launcher" src="https://github.com/user-attachments/assets/659034dc-dfd3-4c9f-bb8c-cbae6f3d8a94" />
<img width="702" height="1560" alt="Screenshot_20260702-132626_Spark Launcher" src="https://github.com/user-attachments/assets/f0af8c4e-f397-4593-9ca6-c5c54bff2c4a" />
<img width="702" height="1560" alt="Screenshot_20260702-132632_Spark Launcher" src="https://github.com/user-attachments/assets/9a7e5e45-336e-4727-b572-c6c7679ae00a" />

| Pick Items | Diff View | File List |
|---|---|---|
| Select original/modified files or folders | Line-by-line diff with change blocks | Modified/Added/Deleted file breakdown |

## Usage

1. Open CompareKit
2. Tap **Pick Original Files/Folder** and select your source
3. Tap **Pick Modified Files/Folder** and select the changed version
4. Browse results by category (All / Modified / Added / Deleted / Unchanged)
5. Tap **Compare** on any file to view the detailed diff
6. Use the menu (⋮) to search, switch views, zoom, or export

## Supported Inputs

- Individual files
- Folders (recursive)
- Zip archives (`.zip`)

## Installation

Download the latest APK from [Releases](../../releases).

## Author

Built by [BlazeFTL](https://github.com/BlazeFTL)

## License

MIT
