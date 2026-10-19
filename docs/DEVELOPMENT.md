# Development

```sh
heavy status
heavy --quick --label flashbackserverbound-build -- ./gradlew --no-daemon build
heavy --label flashbackserverbound-clients -- xvfb-run -a env ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 ./gradlew --no-daemon build verifyRecording verifyStockPlayback
```

The production client runs use Flashback 0.43.6 and Fabric API, driven by a client GameTest (`src/verify`, never part of the release JAR):

- `verifyRecording` (Flashback and this mod): creates a singleplayer world, records a short session (walking,
  sprinting, jumping, strafing, sneaking, turning, clicking, throwing a snowball, changing the hotbar slot), reads the
  saved replay's records back on its own and checks them, then plays the replay to its last tick.
- `verifyStockPlayback` (Flashback alone): plays the same replay to its last tick.

Results go to `build/verify/`: `sample.zip` (the replay), `record-summary.txt`, `playback-with-companion.txt`,
`playback-stock.txt` and the screenshots in each run directory.
