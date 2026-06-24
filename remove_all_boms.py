import os
import glob

def remove_bom(path):
    for root, dirs, files in os.walk(path):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                try:
                    with open(filepath, 'r', encoding='utf-8-sig') as f:
                        content = f.read()
                    with open(filepath, 'w', encoding='utf-8') as f:
                        f.write(content)
                except Exception as e:
                    pass

remove_bom(r'C:\OMC\services\raffle-service\src')
