import os
from PIL import Image, ImageDraw, ImageFont

# Set up paths
dest_dir = "/home/ubuntu/PolarSMP/resourcepack/assets/minecraft/textures/item/custom"
os.makedirs(dest_dir, exist_ok=True)

# Sources
sources = {
    "coin.png": "/home/ubuntu/.gemini/antigravity-cli/brain/476a05f3-0e1f-454d-852b-a4621d39a250/custom_coin_icon_1783653150204.jpg",
    "crate_key.png": "/home/ubuntu/.gemini/antigravity-cli/brain/476a05f3-0e1f-454d-852b-a4621d39a250/custom_key_icon_1783653162619.jpg",
    "crown.png": "/home/ubuntu/.gemini/antigravity-cli/brain/476a05f3-0e1f-454d-852b-a4621d39a250/custom_crown_icon_1783653177563.jpg"
}

def remove_background_and_save(src_path, dest_path):
    print(f"Processing {src_path} -> {dest_path}")
    img = Image.open(src_path).convert("RGBA")
    
    # Simple chroma key: remove pixels that are close to white
    datas = img.getdata()
    new_data = []
    for item in datas:
        # If pixel is very bright (close to white), make it transparent
        if item[0] > 235 and item[1] > 235 and item[2] > 235:
            new_data.append((255, 255, 255, 0))
        else:
            new_data.append(item)
            
    img.putdata(new_data)
    
    # Resize to Minecraft standard 32x32 for high quality pixel art
    resized = img.resize((32, 32), Image.Resampling.NEAREST)
    resized.save(dest_path, "PNG")

# 1. Process main textures
for name, src in sources.items():
    if os.path.exists(src):
        remove_background_and_save(src, os.path.join(dest_dir, name))
    else:
        print(f"Warning: Source file {src} not found!")

# 2. Draw GUI Border programmatically
# A beautiful dark translucent center with a glowing violet/gold border
print("Drawing gui_border.png")
gui_img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
draw = ImageDraw.Draw(gui_img)
# Draw outer frame
for x in range(32):
    for y in range(32):
        if x < 2 or x > 29 or y < 2 or y > 29:
            # Gold/Purple border
            if (x + y) % 2 == 0:
                draw.point((x, y), fill=(255, 215, 0, 255)) # Gold
            else:
                draw.point((x, y), fill=(138, 43, 226, 255)) # Violet
        elif x == 2 or x == 29 or y == 2 or y == 29:
            draw.point((x, y), fill=(0, 0, 0, 200)) # Black inner highlight
        else:
            draw.point((x, y), fill=(0, 0, 0, 100)) # Dark transparent center

gui_img.save(os.path.join(dest_dir, "gui_border.png"), "PNG")

# 3. Draw 10 Rank Badges programmatically
# Shields with gradients and rank numbers 1-10
print("Drawing badge_1.png to badge_10.png")
badge_colors = {
    1: ((138, 43, 226), (75, 0, 130), "I"),    # Rank 1: Deep Indigo/Purple
    2: ((255, 69, 0), (255, 140, 0), "II"),   # Rank 2: Bright Orange/Red
    3: ((0, 191, 255), (0, 0, 255), "III"),    # Rank 3: Cyan/Blue
    4: ((0, 250, 154), (0, 128, 128), "IV"),   # Rank 4: Green/Teal
    5: ((255, 215, 0), (184, 134, 11), "V"),   # Rank 5: Pure Gold
    6: ((192, 192, 192), (105, 105, 105), "VI"), # Rank 6: Silver/Gray
    7: ((205, 127, 50), (139, 69, 19), "VII"),  # Rank 7: Bronze/Brown
    8: ((255, 20, 147), (199, 21, 133), "VIII"),# Rank 8: Deep Pink
    9: ((0, 255, 255), (0, 139, 139), "IX"),   # Rank 9: Cyan
    10: ((50, 205, 50), (34, 139, 34), "X")    # Rank 10: Lime Green
}

for i in range(1, 11):
    badge = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(badge)
    
    color_light, color_dark, roman = badge_colors[i]
    
    # Draw shield/circle base
    # Outer ring
    d.ellipse([2, 2, 29, 29], fill=color_dark, outline=(255, 255, 255, 220), width=1)
    # Inner filling
    d.ellipse([5, 5, 26, 26], fill=color_light)
    
    # Let's draw the rank number "1" - "10" clearly using default font layout
    # Since loading custom fonts can fail if they aren't on the system, we can draw the Roman numerals using lines
    # which is 100% reliable and looks like clean pixel-art markings!
    # For example, let's draw Roman numerals in white pixels in the center of the badge:
    cx, cy = 16, 16
    if roman == "I":
        d.line([16, 10, 16, 21], fill=(255, 255, 255, 255), width=2)
    elif roman == "II":
        d.line([14, 10, 14, 21], fill=(255, 255, 255, 255), width=2)
        d.line([18, 10, 18, 21], fill=(255, 255, 255, 255), width=2)
    elif roman == "III":
        d.line([12, 10, 12, 21], fill=(255, 255, 255, 255), width=2)
        d.line([16, 10, 16, 21], fill=(255, 255, 255, 255), width=2)
        d.line([20, 10, 20, 21], fill=(255, 255, 255, 255), width=2)
    elif roman == "IV":
        # I
        d.line([13, 10, 13, 21], fill=(255, 255, 255, 255), width=2)
        # V
        d.line([17, 10, 19, 21], fill=(255, 255, 255, 255), width=2)
        d.line([21, 10, 19, 21], fill=(255, 255, 255, 255), width=2)
    elif roman == "V":
        d.line([14, 10, 16, 21], fill=(255, 255, 255, 255), width=2)
        d.line([18, 10, 16, 21], fill=(255, 255, 255, 255), width=2)
    elif roman == "VI":
        # V
        d.line([12, 10, 14, 21], fill=(255, 255, 255, 255), width=2)
        d.line([16, 10, 14, 21], fill=(255, 255, 255, 255), width=2)
        # I
        d.line([20, 10, 20, 21], fill=(255, 255, 255, 255), width=2)
    elif roman == "VII":
        # V
        d.line([10, 10, 12, 21], fill=(255, 255, 255, 255), width=2)
        d.line([14, 10, 12, 21], fill=(255, 255, 255, 255), width=2)
        # II
        d.line([17, 10, 17, 21], fill=(255, 255, 255, 255), width=2)
        d.line([21, 10, 21, 21], fill=(255, 255, 255, 255), width=2)
    elif roman == "VIII":
        # V
        d.line([8, 10, 10, 21], fill=(255, 255, 255, 255), width=2)
        d.line([12, 10, 10, 21], fill=(255, 255, 255, 255), width=2)
        # III
        d.line([15, 10, 15, 21], fill=(255, 255, 255, 255), width=2)
        d.line([18, 10, 18, 21], fill=(255, 255, 255, 255), width=2)
        d.line([21, 10, 21, 21], fill=(255, 255, 255, 255), width=2)
    elif roman == "IX":
        # I
        d.line([11, 10, 11, 21], fill=(255, 255, 255, 255), width=2)
        # X
        d.line([15, 10, 21, 21], fill=(255, 255, 255, 255), width=2)
        d.line([21, 10, 15, 21], fill=(255, 255, 255, 255), width=2)
    elif roman == "X":
        d.line([13, 10, 19, 21], fill=(255, 255, 255, 255), width=2)
        d.line([19, 10, 13, 21], fill=(255, 255, 255, 255), width=2)
        
    badge.save(os.path.join(dest_dir, f"badge_{i}.png"), "PNG")

print("All textures processed successfully!")
