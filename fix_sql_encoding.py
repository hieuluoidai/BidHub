import os

sql_dir = 'sql'
output_file = os.path.join(sql_dir, 'all_in_one.sql')

# Define the order: schema first, then migrations
files_to_combine = [os.path.join(sql_dir, 'schema.sql')]
migrations = sorted([os.path.join(sql_dir, f) for f in os.listdir(sql_dir) if f.startswith('migration_') and f.endswith('.sql')])
files_to_combine.extend(migrations)

with open(output_file, 'w', encoding='utf-8') as outfile:
    for file_path in files_to_combine:
        if os.path.exists(file_path):
            with open(file_path, 'r', encoding='utf-8') as infile:
                outfile.write(f"-- Source: {os.path.basename(file_path)}\n")
                outfile.write(infile.read())
                outfile.write("\n\n")

print(f"Successfully combined {len(files_to_combine)} files into {output_file}")
