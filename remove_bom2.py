import os

file = r'C:\OMC\services\raffle-service\src\test\java\com\omc\raffle\application\scheduler\RaffleSchedulerTest.java'
with open(file, 'r', encoding='utf-8-sig') as f:
    content = f.read()

with open(file, 'w', encoding='utf-8') as f:
    f.write(content)
