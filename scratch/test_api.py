import urllib.request
import urllib.error
import json
import unittest
import os

BASE_URL = "http://localhost:24482/api/v1"

class TestPiAgentAPI(unittest.TestCase):

    def request_json(self, path, method="GET", data=None):
        url = f"{BASE_URL}{path}"
        req = urllib.request.Request(url, method=method)
        if data is not None:
            req.add_header("Content-Type", "application/json")
            data_bytes = json.dumps(data).encode("utf-8")
        else:
            data_bytes = None

        try:
            with urllib.request.urlopen(req, data=data_bytes) as response:
                body = response.read().decode("utf-8")
                headers = response.info()
                return response.status, json.loads(body), headers
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8")
            try:
                err_data = json.loads(body)
            except Exception:
                err_data = body
            return e.code, err_data, e.info()

    def test_01_status_endpoint(self):
        """Test the /status health check endpoint."""
        print("Testing Status Endpoint...")
        status, data, headers = self.request_json("/status")
        self.assertEqual(status, 200)
        self.assertEqual(data.get("status"), "ok")
        self.assertIn("neoforge_version", data)
        self.assertIn("loaded_mods", data)
        self.assertGreater(data.get("total_mods", 0), 0)
        # Check CORS
        self.assertEqual(headers.get("Access-Control-Allow-Origin"), "*")

    def test_02_registry_item(self):
        """Test retrieving registered items with pagination."""
        print("Testing Registry Items...")
        status, data, headers = self.request_json("/registry/item?limit=5")
        self.assertEqual(status, 200)
        self.assertIn("total", data)
        self.assertEqual(data.get("limit"), 5)
        results = data.get("results", [])
        self.assertEqual(len(results), 5)
        
        # Verify structure
        for item in results:
            self.assertIn("id", item)
            self.assertIn("translation_key", item)
            self.assertIn("tags", item)

    def test_03_registry_invalid_type(self):
        """Test requesting an unsupported registry type (boundary test)."""
        print("Testing Invalid Registry Type (Boundary)...")
        status, data, headers = self.request_json("/registry/invalid_type")
        self.assertEqual(status, 404)
        self.assertEqual(data.get("code"), 404)
        self.assertEqual(data.get("error"), "Not Found")
        self.assertIn("Unsupported registry type", data.get("message", ""))

    def test_04_limit_clamping_low(self):
        """Test limit clamp below 1 (boundary test)."""
        print("Testing Limit Clamping Low (Boundary)...")
        status, data, headers = self.request_json("/registry/item?limit=-10")
        self.assertEqual(status, 200)
        self.assertEqual(data.get("limit"), 50)  # Should default to 50

    def test_05_limit_clamping_high(self):
        """Test limit clamp above 100 (boundary test)."""
        print("Testing Limit Clamping High (Boundary)...")
        status, data, headers = self.request_json("/registry/item?limit=500")
        self.assertEqual(status, 200)
        self.assertEqual(data.get("limit"), 100) # Should clamp to 100

    def test_06_offset_clamping_negative(self):
        """Test offset clamp below 0 (boundary test)."""
        print("Testing Offset Clamping Negative (Boundary)...")
        status, data, headers = self.request_json("/registry/item?offset=-5")
        self.assertEqual(status, 200)
        self.assertEqual(data.get("offset"), 0)  # Should clamp to 0

    def test_07_namespace_filtering(self):
        """Test namespace query filter."""
        print("Testing Namespace Filtering...")
        status, data, headers = self.request_json("/registry/item?namespace=minecraft&limit=5")
        self.assertEqual(status, 200)
        for item in data.get("results", []):
            self.assertTrue(item.get("id").startswith("minecraft:"))

    def test_08_search_filtering(self):
        """Test keyword search filter."""
        print("Testing Search Filtering...")
        status, data, headers = self.request_json("/registry/item?search=pickaxe&limit=5")
        self.assertEqual(status, 200)
        for item in data.get("results", []):
            self.assertIn("pickaxe", item.get("id").lower())

    def test_09_reload_endpoint(self):
        """Test POST /reload schedules reload correctly."""
        print("Testing Reload Scheduling...")
        status, data, headers = self.request_json("/reload", method="POST")
        self.assertEqual(status, 200)
        self.assertEqual(data.get("status"), "success")
        self.assertIn("scheduled", data.get("message", ""))

    def test_10_recipes_endpoint(self):
        """Test Recipe retrieval and filtering."""
        print("Testing Recipes Querying...")
        status, data, headers = self.request_json("/recipes?limit=5")
        self.assertEqual(status, 200)
        self.assertIn("total", data)
        self.assertGreater(data.get("total", 0), 0)
        recipes = data.get("recipes", [])
        self.assertLessEqual(len(recipes), 5)
        for recipe in recipes:
            self.assertIn("id", recipe)
            self.assertIn("type", recipe)
            self.assertIn("detail", recipe)

        # Output filtering (e.g. check for furnace recipe output)
        status, data, headers = self.request_json("/recipes?output=minecraft:furnace")
        self.assertEqual(status, 200)
        recipes = data.get("recipes", [])
        for recipe in recipes:
            detail = recipe.get("detail", {})
            result = detail.get("result", {})
            res_id = result.get("id") if isinstance(result, dict) else result
            self.assertEqual(res_id, "minecraft:furnace")

    def test_11_loot_tables_endpoint(self):
        """Test Loot Tables retrieval and missing boundary 404."""
        print("Testing Loot Tables...")
        # Valid table
        status, data, headers = self.request_json("/loot-tables?id=minecraft:blocks/coal_ore")
        self.assertEqual(status, 200)
        self.assertIn("pools", data)

        # Nonexistent table
        status, data, headers = self.request_json("/loot-tables?id=minecraft:blocks/nonexistent_table")
        self.assertEqual(status, 404)
        self.assertEqual(data.get("code"), 404)
        self.assertIn("Loot table not found", data.get("message", ""))

    def test_12_advancements_endpoint(self):
        """Test Advancements retrieval."""
        print("Testing Advancements...")
        status, data, headers = self.request_json("/advancements")
        self.assertEqual(status, 200)
        self.assertTrue(isinstance(data, list))
        if len(data) > 0:
            adv = data[0]
            self.assertIn("id", adv)
            self.assertIn("parent", adv)
            self.assertIn("criteria", adv)

    def test_13_export_endpoint(self):
        """Test POST /export creates files correctly."""
        print("Testing Bulk Exporter...")
        export_dir = os.path.abspath("./test_dumps")
        payload = {
            "target_dir": export_dir,
            "export_types": ["item", "block", "recipe", "loot_table"]
        }
        status, data, headers = self.request_json("/export", method="POST", data=payload)
        self.assertEqual(status, 200)
        self.assertEqual(data.get("status"), "success")
        
        # Verify files are on disk and not empty
        files = ["items.json", "blocks.json", "recipes.json", "loot_tables.json"]
        for f in files:
            file_path = os.path.join(export_dir, f)
            self.assertTrue(os.path.exists(file_path), f"File {f} was not written.")
            self.assertGreater(os.path.getsize(file_path), 0, f"File {f} is empty.")
            # Read and verify valid JSON
            with open(file_path, "r", encoding="utf-8") as file_obj:
                parsed = json.load(file_obj)
                self.assertIsNotNone(parsed)

    def test_14_entity_types_endpoint(self):
        """Test retrieving entity types, physical sizes, categories, and default attributes."""
        print("Testing Entity Types Endpoint...")
        # 1. Base query
        status, data, headers = self.request_json("/entity-types?limit=5")
        self.assertEqual(status, 200)
        self.assertIn("total", data)
        self.assertGreater(data.get("total", 0), 0)
        results = data.get("results", [])
        self.assertLessEqual(len(results), 5)

        for ent in results:
            self.assertIn("id", ent)
            self.assertIn("translation_key", ent)
            self.assertIn("category", ent)
            self.assertIn("summonable", ent)
            self.assertIn("fire_immune", ent)
            self.assertIn("width", ent)
            self.assertIn("height", ent)
            self.assertIn("attributes", ent)

        # 2. Search filter
        status, data, headers = self.request_json("/entity-types?search=zombie&limit=5")
        self.assertEqual(status, 200)
        zombies = data.get("results", [])
        self.assertGreater(len(zombies), 0)
        zombie = None
        for z in zombies:
            if z.get("id") == "minecraft:zombie":
                zombie = z
                break
        self.assertIsNotNone(zombie)
        self.assertEqual(zombie.get("category"), "monster")
        # Zombie should have attributes map
        attrs = zombie.get("attributes", {})
        self.assertIn("minecraft:generic.max_health", attrs)
        self.assertEqual(attrs.get("minecraft:generic.max_health"), 20.0)

        # 3. Category filter
        status, data, headers = self.request_json("/entity-types?category=monster&limit=5")
        self.assertEqual(status, 200)
        for ent in data.get("results", []):
            self.assertEqual(ent.get("category"), "monster")

if __name__ == "__main__":
    unittest.main()
