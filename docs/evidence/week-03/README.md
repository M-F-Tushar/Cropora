# Week 3 Evidence

## What was completed

Week 3 added leaf-image input to the Android Scan screen. A user can request camera access, take a photo, or choose an image from the gallery and preview the selected image.

## Evidence

- `Camera Permission.png` — Android camera-permission request opened by Cropora.
- `Scan Photo from Camera or Gallery.png` — Scan screen with camera and gallery actions and an image-preview area.

## How to test

1. Run the Android app and select **Open Scan**.
2. Tap **Take Photo**, grant camera permission, capture a photo, and confirm that it appears in the preview.
3. Tap **Choose from Gallery**, select an image, and confirm that it appears in the preview.
4. Rotate or recreate the screen and confirm that the selected-image state is restored when possible.

This evidence verifies image acquisition and preview only; disease prediction is not connected on this screen yet.
