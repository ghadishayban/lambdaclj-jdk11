layer0/java:
	curl -LO \
	https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.1%2B13/OpenJDK11U-jdk_x64_linux_hotspot_11.0.1_13.tar.gz
	sha256sum -c OpenJDK11U-jdk_x64_linux_hotspot_11.0.1_13.tar.gz.sha256.txt
	mkdir adoptopenjdk
	tar xf OpenJDK11U-jdk_x64_linux_hotspot_11.0.1_13.tar.gz -C adoptopenjdk --strip-components=1
	./adoptopenjdk/bin/jlink \
	  --compress=1 \
	  --dedup-legal-notices=error-if-not-same-content \
	  --strip-debug \
	  --add-modules java.se \
	  --output layer0/java
	rm -rf adoptopenjdk *.tar.gz
	du -sh layer0/java

.PHONY: compile-java
compile-java:
	javac -cp "$(shell clojure -Spath)" --release 11 runtime/com/ghadi/lambda/Bootstrap.java

.PHONY: compile-clj
compile-clj:
	clojure aot.clj

layer0/__runtime: compile-java compile-clj
	mkdir -p layer0/__runtime
	cd runtime && find com/ghadi -name '*.class' -print0 | xargs -0 -I'{}' cp --parents '{}' ../layer0/__runtime

layer0/bootstrap:
	mkdir -p layer0
	cp bootstrap layer0

layer0.zip: layer0/bootstrap layer0/__runtime layer0/java
	cd layer0 && zip -r -9 ../layer0.zip bootstrap __runtime java

sample.zip:
	cd sample && clojure -A:depstar -m hf.depstar.uberjar ../sample.zip

.PHONY: clean
clean:
	rm -rf layer0
