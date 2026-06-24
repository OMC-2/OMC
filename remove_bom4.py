import os
import glob

files = [
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\presentation\controller\RaffleController.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleAppServiceTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleConcurrencyTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\presentation\controller\RaffleControllerTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleResultServiceTest.java'
]

for file in files:
    if os.path.exists(file):
        with open(file, 'r', encoding='utf-8-sig') as f:
            content = f.read()
        with open(file, 'w', encoding='utf-8') as f:
            f.write(content)
