import os

filesToFix = [
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleAppServiceTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleConcurrencyTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleDrawServiceTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\infrastructure\client\PaymentClientTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\presentation\controller\RaffleControllerTest.java'
]

for f in filesToFix:
    with open(f, 'r', encoding='utf-8-sig') as file:
        content = file.read()
    with open(f, 'w', encoding='utf-8') as file:
        file.write(content)
