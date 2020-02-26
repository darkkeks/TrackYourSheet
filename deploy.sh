./gradlew build
rsync build/libs/*.jar dd:tys/ --info=progress2
