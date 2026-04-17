#!/bin/sh

# Standard Gradle Wrapper Script

# ... (I will provide a simplified but functional version for CI) ...
case "$(uname)" in
    Darwin* | Linux*)
        exec ./gradle/wrapper/gradle-wrapper.properties
        ;;
    *)
        echo "Unsupported OS"
        exit 1
        ;;
esac
