import os
files = [
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\application\dto\request\RaffleApplyRequest.java',
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\application\dto\response\RaffleApplyResponse.java',
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\application\event\producer\RaffleWinnerSelectedEvent.java',
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\domain\entity\RaffleEntry.java',
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\infrastructure\client\PaymentClient.java',
    r'C:\OMC\services\raffle-service\src\main\java\com\omc\raffle\presentation\dto\request\RaffleEnterRequest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleAppServiceTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleConcurrencyTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\service\RaffleDrawServiceTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\infrastructure\client\PaymentClientTest.java',
    r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\presentation\controller\RaffleControllerTest.java'
]

for f in files:
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    
    # 1. Main files: UUID billingKeyId -> String billingKeyId
    content = content.replace('UUID billingKeyId', 'String billingKeyId')
    
    # 2. Test files replacements:
    if 'Test.java' in f:
        # For tests, we want to replace UUID.randomUUID() passed as billingKeyId with a string literal
        # But this is tricky without regex. Let's use regex.
        import re
        
        if 'PaymentClientTest.java' in f:
            content = content.replace('UUID billingKeyId = UUID.randomUUID();', 'String billingKeyId = \"bk_\" + UUID.randomUUID().toString();')
            content = content.replace('equalTo(billingKeyId.toString())', 'equalTo(billingKeyId)')
            
        if 'RaffleControllerTest.java' in f:
            content = content.replace('UUID billingKeyId = UUID.randomUUID();', 'String billingKeyId = \"bk_\" + UUID.randomUUID().toString();')
            content = content.replace('UUID.randomUUID(), raffleId, userId, billingKeyId, null,', 'UUID.randomUUID(), raffleId, userId, billingKeyId, null,')
            
        if 'RaffleAppServiceTest.java' in f:
            content = re.sub(r'UUID.randomUUID\(\),\s*UUID.randomUUID\(\),\s*null,', r'\"bk_\" + UUID.randomUUID().toString(), UUID.randomUUID(), null,', content)
            
        if 'RaffleConcurrencyTest.java' in f:
            content = re.sub(r'userId,\s*UUID.randomUUID\(\),\s*null,', r'userId, \"bk_\" + UUID.randomUUID().toString(), null,', content)
            content = re.sub(r'UUID.randomUUID\(\),\s*UUID.randomUUID\(\),\s*null,', r'\"bk_\" + UUID.randomUUID().toString(), UUID.randomUUID(), null,', content)
            
        if 'RaffleDrawServiceTest.java' in f:
            content = re.sub(r'RaffleEntry.create\(raffleId,\s*userId,\s*UUID.randomUUID\(\),\s*null,', r'RaffleEntry.create(raffleId, userId, \"bk_\" + UUID.randomUUID().toString(), null,', content)

    with open(f, 'w', encoding='utf-8') as file:
        file.write(content)
