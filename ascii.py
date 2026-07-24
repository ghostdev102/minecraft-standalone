# Colors using ANSI escape codes
GREEN = "\033[32m"
BLACK = "\033[30m"
RESET = "\033[0m"

# Block character for pixel look
PIXEL = "██"

# Mojang/Minecraft Creeper Face Pattern
creeper_art = [
    [BLACK, BLACK, BLACK, BLACK, BLACK, BLACK, BLACK, BLACK],
    [BLACK, GREEN, GREEN, BLACK, BLACK, GREEN, GREEN, BLACK],
    [BLACK, GREEN, GREEN, BLACK, BLACK, GREEN, GREEN, BLACK],
    [BLACK, BLACK, BLACK, GREEN, GREEN, BLACK, BLACK, BLACK],
    [BLACK, BLACK, GREEN, GREEN, GREEN, GREEN, BLACK, BLACK],
    [BLACK, BLACK, GREEN, GREEN, GREEN, GREEN, BLACK, BLACK],
    [BLACK, BLACK, GREEN, BLACK, BLACK, GREEN, BLACK, BLACK],
]

# Print the colored ASCII art
for row in creeper_art:
    line = ""
    for color in row:
        line += f"{color}{PIXEL}"
    print(line + RESET)
