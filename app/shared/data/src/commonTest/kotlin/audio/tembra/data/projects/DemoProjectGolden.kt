package audio.tembra.data.projects

/**
 * The exact text of `specs/009-timeline-arranger/fixtures/demo-project.json`: the codec's output for
 * `DemoProject.build(DemoProjectIds.fixed())`. The committed fixture must stay byte-identical to this constant
 * (`DemoProjectGoldenFileTest` on the JVM lane proves it); a change here is a document-format change.
 */
internal object DemoProjectGolden {
    val TEXT: String = """
        {
          "formatVersion": 1,
          "project": {
            "id": "0b6f1c9a-4d2e-4f7a-9c1b-2e5d8a7f3c10",
            "name": "Demo project",
            "description": "Four loops to play with",
            "key": "Dmin",
            "tempo": 124,
            "timeSignature": "4/4",
            "masterVolume": 80,
            "metronomeOn": false,
            "assets": {
              "5f495ce910cc8fb7f3a1d16f3098edc61b53f8c64c7db2e4f4894cd870ce7638": {
                "fileName": "drums.wav",
                "format": "wav",
                "channels": 1,
                "sampleRate": 48000,
                "frames": 1486452,
                "byteSize": 2972948
              },
              "a5c8eaa3534a1f79d0f7477e6b11fe272c9cf3d80e1ea79454c04701b6916a6d": {
                "fileName": "bass.wav",
                "format": "wav",
                "channels": 1,
                "sampleRate": 48000,
                "frames": 1486452,
                "byteSize": 2972948
              },
              "781b436a38c04b4588d8345226291f1b0816127b8fcf589e3ddc7ff2ef62cebf": {
                "fileName": "keys.wav",
                "format": "wav",
                "channels": 2,
                "sampleRate": 48000,
                "frames": 1486452,
                "byteSize": 5945852
              },
              "ab752321439c50f4c0ff2b990d07ce4e58d5345f56ce3e1e5adfca2206109869": {
                "fileName": "pad.wav",
                "format": "wav",
                "channels": 2,
                "sampleRate": 48000,
                "frames": 1486452,
                "byteSize": 5945852
              }
            },
            "tracks": [
              {
                "id": "7d1c0a2e-0000-4000-8000-000000000001",
                "name": "Drums",
                "label": "drums",
                "colorIndex": 0,
                "gainDb": 0.0,
                "muted": false,
                "soloed": false,
                "clips": [
                  {
                    "id": "9e4f6b1d-0000-4000-8000-000000000001",
                    "assetId": "5f495ce910cc8fb7f3a1d16f3098edc61b53f8c64c7db2e4f4894cd870ce7638",
                    "startBeats": 0.0,
                    "offsetFrames": 0,
                    "lengthFrames": 1486452,
                    "name": "drums"
                  }
                ]
              },
              {
                "id": "7d1c0a2e-0000-4000-8000-000000000002",
                "name": "Bass",
                "label": "bass",
                "colorIndex": 1,
                "gainDb": 0.0,
                "muted": false,
                "soloed": false,
                "clips": [
                  {
                    "id": "9e4f6b1d-0000-4000-8000-000000000002",
                    "assetId": "a5c8eaa3534a1f79d0f7477e6b11fe272c9cf3d80e1ea79454c04701b6916a6d",
                    "startBeats": 0.0,
                    "offsetFrames": 0,
                    "lengthFrames": 1486452,
                    "name": "bass"
                  }
                ]
              },
              {
                "id": "7d1c0a2e-0000-4000-8000-000000000003",
                "name": "Keys",
                "label": "keys",
                "colorIndex": 2,
                "gainDb": 0.0,
                "muted": false,
                "soloed": false,
                "clips": [
                  {
                    "id": "9e4f6b1d-0000-4000-8000-000000000003",
                    "assetId": "781b436a38c04b4588d8345226291f1b0816127b8fcf589e3ddc7ff2ef62cebf",
                    "startBeats": 0.0,
                    "offsetFrames": 0,
                    "lengthFrames": 1486452,
                    "name": "keys"
                  }
                ]
              },
              {
                "id": "7d1c0a2e-0000-4000-8000-000000000004",
                "name": "Pad",
                "label": "synth",
                "colorIndex": 3,
                "gainDb": 0.0,
                "muted": false,
                "soloed": false,
                "clips": [
                  {
                    "id": "9e4f6b1d-0000-4000-8000-000000000004",
                    "assetId": "ab752321439c50f4c0ff2b990d07ce4e58d5345f56ce3e1e5adfca2206109869",
                    "startBeats": 0.0,
                    "offsetFrames": 0,
                    "lengthFrames": 743226,
                    "name": "pad"
                  },
                  {
                    "id": "9e4f6b1d-0000-4000-8000-000000000005",
                    "assetId": "ab752321439c50f4c0ff2b990d07ce4e58d5345f56ce3e1e5adfca2206109869",
                    "startBeats": 32.0,
                    "offsetFrames": 743226,
                    "lengthFrames": 743226,
                    "name": "pad"
                  }
                ]
              }
            ],
            "countIn": "ONE_BAR"
          }
        }
    """.trimIndent() + "\n"
}
