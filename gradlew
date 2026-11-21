#!/usr/bin/env bash
export JAVA_HOME="${JAVA_HOME:-/home/wolly/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2}"
export ANDROID_HOME="${ANDROID_HOME:-/home/wolly/Android/Sdk}"
exec /home/wolly/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle "$@"
