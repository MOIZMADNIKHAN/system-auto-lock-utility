# 🧠 System Auto Lock Utility

A smart desktop utility built in **Java** that automatically locks the workstation when the user is idle or not detected via webcam.  
It combines **user activity monitoring (keyboard & mouse)** with **real-time face detection using OpenCV**, ensuring both **security** and **convenience**.

---

## 🚀 Features

- 🔒 **Automatic System Lock**
  - Locks your PC when no user activity or face is detected.
- 🧍‍♂️ **Face Detection**
  - Uses OpenCV Haar Cascade (`haarcascade_frontalface_alt.xml`) to verify user presence.
- ⌨️ **User Activity Monitoring**
  - Detects keyboard and mouse usage through Windows API via JNA.
- 🎥 **Intelligent Camera Control**
  - Camera only activates when system is idle — saves power and reduces unnecessary access.
- ⚙️ **Configurable Thresholds**
  - Customize idle timeout and detection sensitivity.
- 🪟 **Windows Compatible**
  - Uses native Windows APIs to detect user input and lock the workstation.

---

## 🧰 Tech Stack

| Component | Description |
|------------|-------------|
| **Language** | Java 8 |
| **Libraries** | OpenCV (via `org.bytedeco.opencv-platform`), JNA (for Windows API) |
| **Build Tool** | Maven |
| **OS Support** | Windows 10 / 11 |

---

## 🧩 Project Structure

