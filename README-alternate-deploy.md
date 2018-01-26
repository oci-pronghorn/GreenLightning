# GreenLightning - Alternate Build Options

Below are known alternate deployment options.


## Excelsior JET - Convert to native executable

 1. Download and install [Excelsior JET](https://www.excelsiorjet.com)
    (the 90-day trial will work just fine).
 2. Do one of the following:
      * Add the Excelsior JET `bin/` directory to `PATH` and run `mvn jet:build`.
      * Run `mvn jet:build -Djet.home=`*JET-Home*
 3. Run `go-native.sh`.

The zip file in `target/jet` can be copied to other systems.

