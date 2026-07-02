import os
import json
import sqlite3
import re

# Database and source paths
DUMPS_DIR = "./.pi-agent/dumps"
DB_PATH = "./minecraft_data.db"

def init_db(db_path):
    """Initialize SQLite database with structured schema and search indexes."""
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # 1. Items table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS items (
        id TEXT PRIMARY KEY,
        translation_key TEXT,
        tags TEXT,
        raw_json TEXT
    )
    ''')
    
    # 2. Blocks table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS blocks (
        id TEXT PRIMARY KEY,
        translation_key TEXT,
        hardness REAL,
        light_emission INTEGER,
        tags TEXT,
        raw_json TEXT
    )
    ''')
    
    # 3. Recipes table
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS recipes (
        id TEXT PRIMARY KEY,
        type TEXT,
        output_item_id TEXT,
        output_count INTEGER,
        raw_json TEXT
    )
    ''')
    
    # 4. Recipe inputs table (for many-to-many relationship)
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS recipe_inputs (
        recipe_id TEXT,
        input_item_id TEXT,
        FOREIGN KEY(recipe_id) REFERENCES recipes(id)
    )
    ''')
    
    # 5. Tags table (for item/block tags)
    cursor.execute('''
    CREATE TABLE IF NOT EXISTS tags (
        type TEXT,
        tag_id TEXT,
        member_id TEXT
    )
    ''')
    
    # Create indexes for optimized agent lookup
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_recipe_inputs ON recipe_inputs(input_item_id)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_recipe_outputs ON recipes(output_item_id)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_tags_lookup ON tags(tag_id, member_id)')
    cursor.execute('CREATE INDEX IF NOT EXISTS idx_items_search ON items(id)')
    
    conn.commit()
    return conn

def recursive_find_inputs(data, output_id):
    """Recursively search recipe JSON to extract all input ingredients while skipping output fields."""
    inputs = set()
    
    def traverse(val):
        if isinstance(val, dict):
            for k, v in val.items():
                if k in ("result", "output"):  # Skip outputs
                    continue
                traverse(v)
        elif isinstance(val, list):
            for item in val:
                traverse(item)
        elif isinstance(val, str):
            # Matches item IDs like 'minecraft:iron_ingot' or tag references
            if ":" in val and val != output_id:
                inputs.add(val)
                
    traverse(data)
    return list(inputs)

def extract_output(detail):
    """Extract output ID and count from recipe details."""
    output_id = None
    output_count = 1
    
    if not isinstance(detail, dict):
        return None, 1
        
    # Standard format: detail.result
    if "result" in detail:
        res = detail["result"]
        if isinstance(res, str):
            output_id = res
        elif isinstance(res, dict):
            output_id = res.get("id") or res.get("item")
            output_count = res.get("count") or res.get("amount") or 1
            
    # Fallback format: detail.output
    elif "output" in detail:
        res = detail["output"]
        if isinstance(res, str):
            output_id = res
        elif isinstance(res, dict):
            output_id = res.get("id") or res.get("item")
            output_count = res.get("count") or 1
            
    return output_id, output_count

def populate_database(dumps_dir, conn):
    cursor = conn.cursor()
    
    # --- 1. Import Items ---
    items_file = os.path.join(dumps_dir, "items.json")
    if os.path.exists(items_file):
        print("Importing items...")
        with open(items_file, "r", encoding="utf-8") as f:
            data = json.load(f)
            for item in data.get("results", []):
                item_id = item.get("id")
                tags_str = json.dumps(item.get("tags", []))
                cursor.execute(
                    "INSERT OR REPLACE INTO items (id, translation_key, tags, raw_json) VALUES (?, ?, ?, ?)",
                    (item_id, item.get("translation_key"), tags_str, json.dumps(item))
                )
                
    # --- 2. Import Blocks ---
    blocks_file = os.path.join(dumps_dir, "blocks.json")
    if os.path.exists(blocks_file):
        print("Importing blocks...")
        with open(blocks_file, "r", encoding="utf-8") as f:
            data = json.load(f)
            for block in data.get("results", []):
                block_id = block.get("id")
                tags_str = json.dumps(block.get("tags", []))
                cursor.execute(
                    "INSERT OR REPLACE INTO blocks (id, translation_key, hardness, light_emission, tags, raw_json) VALUES (?, ?, ?, ?, ?, ?)",
                    (block_id, block.get("translation_key"), block.get("hardness", 0.0), block.get("light_emission", 0), tags_str, json.dumps(block))
                )

    # --- 3. Import Tags ---
    tags_file = os.path.join(dumps_dir, "tags.json")
    if os.path.exists(tags_file):
        print("Importing tags...")
        with open(tags_file, "r", encoding="utf-8") as f:
            data = json.load(f)
            # Item tags
            for tag_id, members in data.get("item", {}).items():
                for member in members:
                    cursor.execute("INSERT INTO tags (type, tag_id, member_id) VALUES (?, ?, ?)", ("item", tag_id, member))
            # Block tags
            for tag_id, members in data.get("block", {}).items():
                for member in members:
                    cursor.execute("INSERT INTO tags (type, tag_id, member_id) VALUES (?, ?, ?)", ("block", tag_id, member))

    # --- 4. Import Recipes & Inputs ---
    recipes_file = os.path.join(dumps_dir, "recipes.json")
    if os.path.exists(recipes_file):
        print("Importing recipes...")
        with open(recipes_file, "r", encoding="utf-8") as f:
            data = json.load(f)
            for recipe in data.get("recipes", []):
                recipe_id = recipe.get("id")
                recipe_type = recipe.get("type")
                detail = recipe.get("detail", {})
                
                output_id, output_count = extract_output(detail)
                
                cursor.execute(
                    "INSERT OR REPLACE INTO recipes (id, type, output_item_id, output_count, raw_json) VALUES (?, ?, ?, ?, ?)",
                    (recipe_id, recipe_type, output_id, output_count, json.dumps(recipe))
                )
                
                # Extract and insert inputs
                inputs = recursive_find_inputs(detail, output_id)
                for inp in inputs:
                    cursor.execute(
                        "INSERT INTO recipe_inputs (recipe_id, input_item_id) VALUES (?, ?)",
                        (recipe_id, inp)
                    )
                    
    conn.commit()
    print("Database built successfully!")

if __name__ == "__main__":
    if not os.path.exists(DUMPS_DIR):
        print(f"Error: Directory '{DUMPS_DIR}' not found. Please run '/pi-agent export' in Minecraft first.")
    else:
        # Clear existing DB
        if os.path.exists(DB_PATH):
            os.remove(DB_PATH)
        conn = init_db(DB_PATH)
        populate_database(DUMPS_DIR, conn)
        conn.close()
