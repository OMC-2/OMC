import os
import glob

files = [
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\domain\entity\OutboxEvent.java',
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\domain\entity\Raffle.java',
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\domain\entity\RaffleEntry.java',
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\domain\entity\RaffleResult.java'
]

for file in files:
    if os.path.exists(file):
        with open(file, 'r', encoding='utf-8-sig') as f:
            content = f.read()
        with open(file, 'w', encoding='utf-8') as f:
            f.write(content)
