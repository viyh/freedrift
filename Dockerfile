FROM eclipse-temurin:17-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_HOME=/opt/gradle \
    GRADLE_USER_HOME=/root/.gradle \
    DEBIAN_FRONTEND=noninteractive

ENV PATH=$GRADLE_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

RUN apt-get update && apt-get install -y --no-install-recommends \
      curl unzip ca-certificates git \
    && rm -rf /var/lib/apt/lists/*

# Android command-line tools
ARG CMDLINE_TOOLS_VERSION=11076708
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
 && curl -fsSLo /tmp/tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
 && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/tools.zip

# SDK packages + licenses
RUN yes | sdkmanager --licenses >/dev/null \
 && sdkmanager --install \
      "platform-tools" \
      "platforms;android-35" \
      "build-tools;35.0.0"

# Gradle
ARG GRADLE_VERSION=8.10.2
RUN curl -fsSLo /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
 && unzip -q /tmp/gradle.zip -d /opt \
 && mv "/opt/gradle-${GRADLE_VERSION}" $GRADLE_HOME \
 && rm /tmp/gradle.zip

WORKDIR /workspace
