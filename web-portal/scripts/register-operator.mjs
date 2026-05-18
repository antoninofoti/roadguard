import { initializeApp } from "firebase/app";
import { getAuth, createUserWithEmailAndPassword } from "firebase/auth";

const config = {
  apiKey: "AIzaSyA6w3k0Ywq9pWi8ecHRyMFmZMvQTT9XEnk",
  authDomain: "roadguard-7b17f.firebaseapp.com",
  projectId: "roadguard-7b17f",
};

const app = initializeApp(config);
const auth = getAuth(app);

async function register() {
  try {
    await createUserWithEmailAndPassword(auth, "operator.release@roadguard.local", "RoadGuard!2026");
    console.log("SUCCESS: Created operator.release@roadguard.local / RoadGuard!2026 on Cloud Firebase!");
  } catch (e) {
    console.log("INFO/ERROR: Account might already exist or failed. Details: " + e.message);
  }
  process.exit(0);
}

register();
