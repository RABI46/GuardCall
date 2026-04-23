# GuardCall

GuardCall is an iOS application that uses CallKit to provide call blocking and identification services.

## Project Structure

This project uses [XcodeGen](https://github.com/yonaskolb/XcodeGen) to manage the Xcode project file. The project configuration is defined in `project.yml`.

- `GuardCall/`: Main application source code.
- `GuardCallDirectory/`: Call Directory extension source code.
- `project.yml`: XcodeGen project specification.

## Getting Started

### Prerequisites

You must have [XcodeGen](https://github.com/yonaskolb/XcodeGen) installed. You can install it via Homebrew:

```bash
brew install xcodegen
```

### Generating the Xcode Project

To generate the `.xcodeproj` file, run the following command in the root directory:

```bash
xcodegen generate
```

This will create `GuardCall.xcodeproj`, which you can then open in Xcode.

## Requirements

- iOS 18.0+
- Swift 5.10+
- Xcode 16.0+
