#!/usr/bin/env python3
"""
MacroMate - System 1 Decision Model Builder & ONNX Exporter

Trains a multi-head decision classifier on sentence-transformers/all-MiniLM-L6-v2 and
exports an INT8-quantized ONNX asset (+ WordPiece vocab.txt) for on-device Android execution
via ONNX Runtime Mobile (see LayaDecisionEngine.kt).

This is the actual script that produced the laya_decision.onnx bundled in
app/src/main/assets/ — paths are relative to the repo root so it's portable across machines;
the original training run hardcoded an absolute path on the training box.

Usage:
    pip install torch transformers onnx onnxruntime
    python scripts/export_laya_onnx.py
"""

import os
import shutil
import time
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from transformers import AutoTokenizer, AutoModel
from onnxruntime.quantization import quantize_dynamic, QuantType

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets")
BUILD_DIR = os.path.join(REPO_ROOT, "scripts", "build")

# 1. Dataset Definition
TRAINING_DATA = [
    # (query, goal_idx, budget_idx, is_veg_idx)
    # Goal: 0: HIGH_PROTEIN, 1: KETO_FAT_LOSS, 2: LEAN_MASS_BULK, 3: LOW_CALORIE_CLEAN, 4: QUICK_HYDRATION_SNACK
    # Budget: 0: BUDGET_UNDER_200, 1: STANDARD_UNDER_400, 2: PREMIUM_UNRESTRICTED
    # Veg: 0: Non-Veg, 1: Pure Veg
    ("high protein lunch under 300 rupees", 0, 1, 0),
    ("pure veg paneer meal with 40g protein", 0, 1, 1),
    ("chicken breast meal with broccoli and rice", 0, 1, 0),
    ("egg whites and soya chunks diet bowl", 0, 0, 0),
    ("need 50 grams of protein post workout", 0, 1, 0),
    ("grilled fish with lemon herb sauce", 0, 2, 0),
    ("tofu scramble with sauteed spinach vegan", 0, 1, 1),
    ("cheap high protein snack under 150", 0, 0, 0),
    ("luxury salmon steak high protein dinner", 0, 2, 0),
    ("sprouts salad with boiled chana 25g protein", 0, 0, 1),
    ("double chicken breast with brown rice", 0, 1, 0),
    ("paneer tikka with green chutney high protein", 0, 1, 1),

    # Keto Fat Loss
    ("keto dinner low carb under 350", 1, 1, 0),
    ("strict keto chicken salad olive oil dressing", 1, 1, 0),
    ("zero carb paneer butter roasted veggies", 1, 1, 1),
    ("keto egg omelette with cheese and mushrooms", 1, 0, 0),
    ("low carb avocado and grilled chicken bowl", 1, 2, 0),
    ("keto mutton kebab with mint chutney no roti", 1, 2, 0),
    ("cauliflower rice with butter chicken keto", 1, 1, 0),
    ("boiled eggs and black pepper keto breakfast", 1, 0, 0),
    ("keto almond flour roti with paneer bhurji", 1, 1, 1),

    # Lean Mass Bulk
    ("clean bulk meal extra rice and double chicken", 2, 1, 0),
    ("heavy mass gainer meal under 400 calories not an issue", 2, 1, 0),
    ("chicken biryani with extra leg piece and boiled eggs", 2, 1, 0),
    ("paneer butter masala with 4 butter naans bulk", 2, 1, 1),
    ("high carb mass builder mutton pulav", 2, 2, 0),
    ("dal makhani jeera rice with extra ghee bulk", 2, 1, 1),
    ("big bowl chicken fried rice with chilli chicken", 2, 1, 0),
    ("bulk diet peanut butter oats and banana smoothie bowl", 2, 0, 1),

    # Low Calorie Clean
    ("low calorie salad for weight loss under 200", 3, 0, 1),
    ("light vegetable clear soup with steamed momos", 3, 0, 1),
    ("diet cucumber tomato salad fat loss under 150", 3, 0, 1),
    ("plain moong dal khichdi easy digestion light meal", 3, 0, 1),
    ("steamed chicken momos with clear broth", 3, 0, 0),
    ("quinoa salad with olive oil dressing light", 3, 1, 1),
    ("fruit bowl with papaya watermelon and chia seeds", 3, 0, 1),
    ("boiled sweet potato and green tea snack", 3, 0, 1),

    # Quick Hydration / Snack
    ("fresh coconut water and lime soda", 4, 0, 1),
    ("cold buttermilk chaas with roasted cumin", 4, 0, 1),
    ("sweet lassi or fresh watermelon juice", 4, 0, 1),
    ("electrolytes cold pressed sugarcane juice", 4, 0, 1),
    ("iced black coffee zero sugar energy boost", 4, 0, 1),
    ("tender coconut shake refreshing snack", 4, 0, 1),
    ("fresh orange juice no sugar after workout", 4, 0, 1),
]

# Duplicate and augment slightly to ensure strong convergence
AUGMENTED_DATA = []
for q, g, b, v in TRAINING_DATA:
    AUGMENTED_DATA.append((q, g, b, v))
    AUGMENTED_DATA.append((f"please suggest {q}", g, b, v))
    AUGMENTED_DATA.append((f"order me {q}", g, b, v))
    AUGMENTED_DATA.append((f"looking for {q}", g, b, v))

class FoodIntentDataset(Dataset):
    def __init__(self, data, tokenizer, max_len=64):
        self.data = data
        self.tokenizer = tokenizer
        self.max_len = max_len

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        query, goal, budget, veg = self.data[idx]
        encoding = self.tokenizer(
            query,
            truncation=True,
            padding="max_length",
            max_length=self.max_len,
            return_tensors="pt"
        )
        return {
            "input_ids": encoding["input_ids"].squeeze(0),
            "attention_mask": encoding["attention_mask"].squeeze(0),
            "goal": torch.tensor(goal, dtype=torch.long),
            "budget": torch.tensor(budget, dtype=torch.long),
            "veg": torch.tensor(veg, dtype=torch.long)
        }

# 2. Multi-Head Decision Model Architecture
class SystemOneDecisionModel(nn.Module):
    def __init__(self, encoder_name="sentence-transformers/all-MiniLM-L6-v2"):
        super().__init__()
        self.encoder = AutoModel.from_pretrained(encoder_name)
        hidden_dim = self.encoder.config.hidden_size  # 384 for MiniLM

        self.dropout = nn.Dropout(0.1)
        # Decision Heads
        self.goal_head = nn.Linear(hidden_dim, 5)    # 5 MealGoalTypes
        self.budget_head = nn.Linear(hidden_dim, 3)  # 3 BudgetTiers
        self.veg_head = nn.Linear(hidden_dim, 2)     # Binary Noul: Vegetarian?

    def forward(self, input_ids, attention_mask):
        outputs = self.encoder(input_ids=input_ids, attention_mask=attention_mask)
        # Use mean pooling over token embeddings
        token_embeddings = outputs[0]
        input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
        sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, 1)
        sum_mask = torch.clamp(input_mask_expanded.sum(1), min=1e-9)
        pooled = sum_embeddings / sum_mask

        pooled = self.dropout(pooled)
        goal_logits = self.goal_head(pooled)
        budget_logits = self.budget_head(pooled)
        veg_logits = self.veg_head(pooled)

        return goal_logits, budget_logits, veg_logits


def train_and_export():
    os.makedirs(BUILD_DIR, exist_ok=True)
    os.makedirs(ASSETS_DIR, exist_ok=True)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[*] Training on device: {device} ({torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'})")

    model_name = "sentence-transformers/all-MiniLM-L6-v2"
    print(f"[*] Downloading backbone: {model_name}...")
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = SystemOneDecisionModel(model_name).to(device)

    dataset = FoodIntentDataset(AUGMENTED_DATA, tokenizer)
    loader = DataLoader(dataset, batch_size=16, shuffle=True)

    optimizer = torch.optim.AdamW(model.parameters(), lr=3e-5, weight_decay=0.01)
    criterion = nn.CrossEntropyLoss()

    print(f"[*] Training multi-head decision classifier on {len(AUGMENTED_DATA)} samples...")
    print("[!] NOTE: this is a tiny hand-written dataset (~44 examples x4 augmentations) meant")
    print("    to get a working demo model — accuracy on phrasing very different from the")
    print("    examples above will be weaker. Add more labeled examples here to improve it.")
    model.train()
    start_time = time.time()
    for epoch in range(12):
        total_loss = 0
        for batch in loader:
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            goals = batch["goal"].to(device)
            budgets = batch["budget"].to(device)
            vegs = batch["veg"].to(device)

            optimizer.zero_grad()
            goal_out, budget_out, veg_out = model(input_ids, attention_mask)

            loss = criterion(goal_out, goals) + criterion(budget_out, budgets) + criterion(veg_out, vegs)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        if (epoch + 1) % 4 == 0:
            print(f"    Epoch {epoch+1:02d}/12 | Loss: {total_loss/len(loader):.4f}")

    print(f"[OK] Training completed in {time.time() - start_time:.2f} seconds!")

    # 3. Export to ONNX
    print("[*] Exporting to ONNX format...")
    model.eval()
    model.to("cpu")

    dummy_input = tokenizer("high protein lunch under 300 rupees", return_tensors="pt")
    dummy_input_ids = dummy_input["input_ids"]
    dummy_attention_mask = dummy_input["attention_mask"]

    onnx_fp32_path = os.path.join(BUILD_DIR, "laya_decision_fp32.onnx")
    torch.onnx.export(
        model,
        (dummy_input_ids, dummy_attention_mask),
        onnx_fp32_path,
        input_names=["input_ids", "attention_mask"],
        output_names=["goal_logits", "budget_logits", "veg_logits"],
        dynamic_axes={
            "input_ids": {0: "batch_size", 1: "sequence_length"},
            "attention_mask": {0: "batch_size", 1: "sequence_length"},
            "goal_logits": {0: "batch_size"},
            "budget_logits": {0: "batch_size"},
            "veg_logits": {0: "batch_size"}
        },
        opset_version=17,
        dynamo=False
    )
    fp32_size = os.path.getsize(onnx_fp32_path) / (1024 * 1024)
    print(f"[OK] FP32 ONNX exported: {onnx_fp32_path} ({fp32_size:.1f} MB)")

    # 4. Quantize to INT8
    print("[*] Quantizing model to INT8 for mobile NPU/CPU execution...")
    onnx_int8_path = os.path.join(BUILD_DIR, "laya_decision.onnx")
    quantize_dynamic(
        model_input=onnx_fp32_path,
        model_output=onnx_int8_path,
        weight_type=QuantType.QInt8
    )
    int8_size = os.path.getsize(onnx_int8_path) / (1024 * 1024)
    print(f"[OK] INT8 quantized ONNX created: {onnx_int8_path} ({int8_size:.1f} MB)")

    # 5. Copy model + tokenizer vocab into Android assets
    target_asset = os.path.join(ASSETS_DIR, "laya_decision.onnx")
    shutil.copyfile(onnx_int8_path, target_asset)
    print(f"[OK] Copied quantized model to Android assets: {target_asset}")

    tokenizer.save_vocabulary(ASSETS_DIR)
    print(f"[OK] Exported tokenizer vocabulary to: {ASSETS_DIR}/vocab.txt")

    # 6. Verify with ONNX Runtime (sanity check before shipping)
    print("\n[*] Verifying ONNX Runtime inference & latency (CPU, this training box):")
    import onnxruntime as ort
    sess = ort.InferenceSession(onnx_int8_path, providers=["CPUExecutionProvider"])

    test_queries = [
        "high protein lunch under 300 rupees",
        "strict keto paneer butter masala no carbs",
        "fresh cold watermelon juice snack under 100",
        "clean bulk meal extra chicken and rice"
    ]

    goal_names = ["HIGH_PROTEIN", "KETO_FAT_LOSS", "LEAN_MASS_BULK", "LOW_CALORIE_CLEAN", "QUICK_HYDRATION_SNACK"]
    budget_names = ["BUDGET_UNDER_200", "STANDARD_UNDER_400", "PREMIUM_UNRESTRICTED"]

    for q in test_queries:
        enc = tokenizer(q, return_tensors="np")
        t0 = time.perf_counter()
        outputs = sess.run(None, {
            "input_ids": enc["input_ids"],
            "attention_mask": enc["attention_mask"]
        })
        latency = (time.perf_counter() - t0) * 1000

        goal_pred = goal_names[outputs[0].argmax()]
        budget_pred = budget_names[outputs[1].argmax()]
        veg_pred = "VEG" if outputs[2].argmax() == 1 else "NON-VEG"

        print(f" -> Query: \"{q}\"")
        print(f"    Decision: Goal={goal_pred} | Budget={budget_pred} | Diet={veg_pred} | Latency: {latency:.2f}ms")
        print("    (this is server-CPU latency, not a phone NPU measurement — benchmark on")
        print("     target hardware separately before quoting a device latency number)\n")

if __name__ == "__main__":
    train_and_export()
