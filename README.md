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
systemAutoLockUtility/
│
├── src/main/java/com/microservices/autolock/
│ ├── AutoLockMain.java # Application entry point
│ ├── FaceWatchService.java # Face detection & camera logic
│ ├── UserActivityMonitor.java # Detects keyboard & mouse inactivity
│ ├── Kernel32Ext.java # Windows native JNA API
│
├── src/main/resources/
│ └── haarcascade_frontalface_alt.xml
│
├── pom.xml
└── README.md

## ⚡ How It Works

1. Monitors user activity (keyboard + mouse).  
2. If idle for **N seconds**, activates the camera to detect a face.  
3. If **no face is detected** for a continuous period, it executes:  

   ```bash
   rundll32.exe user32.dll,LockWorkStation


Once activity resumes, the camera is released immediately.

🧑‍💻 How to Run
Prerequisites

Java 8 installed

Maven installed (mvn -v)

Webcam properly connected

Windows OS

Commands
# Clone the repository
git clone https://github.com/<your-username>/systemAutoLockUtility.git

# Navigate to project folder
cd systemAutoLockUtility

# Build
mvn clean install

# Run the app
mvn exec:java -Dexec.mainClass="com.microservices.autolock.FaceWatchService"

