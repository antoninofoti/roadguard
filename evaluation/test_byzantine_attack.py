import sys
import os
import numpy as np
import hashlib
import hmac
import logging
import json

# Setup logging to capture the warning
logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

# Add the evaluation directory to path to import fl_fedavg
sys.path.insert(0, os.path.join(os.getcwd(), "evaluation"))

try:
    import fl_fedavg
except ImportError:
    # Fallback if pathing is tricky in the environment
    sys.path.insert(0, os.getcwd())
    import fl_fedavg

def run_byzantine_attack_simulation():
    print("=== RoadGuard Security Audit: Byzantine Attack Simulation ===")
    
    # 1. Setup a dummy update
    client_id = 2
    weights = [np.random.randn(10, 10).astype(np.float32)]
    secret = fl_fedavg.FL_HMAC_SECRET
    
    # 2. Client signs the valid update
    print(f"[Client {client_id}] Computing local update and signing...")
    valid_signature = fl_fedavg.sign_model_update(weights, secret)
    print(f"[Client {client_id}] Signature: {valid_signature[:16]}...")
    
    # 3. Adversary tampers with the weights during transmission
    print(f"\n[Adversary] Intercepting update from Client {client_id}...")
    tampered_weights = [weights[0].copy()]
    tampered_weights[0][0, 0] += 0.005  # Injecting poisoning noise
    print(f"[Adversary] Weights tampered at index [0,0].")
    
    # 4. Aggregator verifies the update
    print(f"\n[Aggregator] Verifying update from Client {client_id} before FedAvg...")
    is_valid = fl_fedavg.verify_model_update(tampered_weights, valid_signature, secret)
    
    if not is_valid:
        # Manually trigger the log that would happen in the loop
        logging.warning(f"client {client_id} update rejected — HMAC mismatch")
        print("\n[RESULT] Attack BLOCKED: The poisoned update was successfully detected and rejected.")
        return True
    else:
        print("\n[RESULT] Attack SUCCESSFUL: The system failed to detect the poisoning (CRITICAL FAILURE).")
        return False

if __name__ == "__main__":
    success = run_byzantine_attack_simulation()
    if success:
        print("\nSecurity verification: PASSED")
    else:
        sys.exit(1)
