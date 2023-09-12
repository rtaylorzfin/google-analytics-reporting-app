import pandas as pd
import matplotlib.pyplot as plt
import sys

# Ensure an argument is given
if len(sys.argv) < 2:
    print("Please provide a CSV file as an argument.")
    sys.exit()

# The first argument after the script name will be the CSV file
csv_file = sys.argv[1]

# Load data
data = pd.read_csv(csv_file)

# Group by 'ga:countryIsoCode' and sum the metrics
grouped_data = data.groupby('ga:countryIsoCode').agg({
    'users': 'sum'
}).reset_index().sort_values(by='users', ascending=False)

# Plotting the data
plt.figure(figsize=(15,8))
plt.bar(grouped_data['ga:countryIsoCode'], grouped_data['users'], color='blue')
plt.xlabel('Country ISO Code')
plt.ylabel('Total Users')
plt.title('Users by Country')
plt.xticks(rotation=45)  # Rotating country codes for better visibility
plt.tight_layout()

plt.show()
