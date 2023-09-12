import pandas as pd
import matplotlib.pyplot as plt
import sys

#ga:date,ga:countryIsoCode,ga:regionIsoCode,users,sessions,bounces,sessionDuration

# Ensure an argument is given
if len(sys.argv) < 2:
    print("Please provide a CSV file as an argument.")
    sys.exit()

# The first argument after the script name will be the CSV file
csv_file = sys.argv[1]

# Load data
data = pd.read_csv(csv_file)

# Convert 'ga:date' from string to datetime
data['ga:date'] = pd.to_datetime(data['ga:date'], format='%Y%m%d')

# Group by 'ga:date' and sum the metrics
grouped_data = data.groupby('ga:date').agg({
    'pageviews': 'sum',
    'users': 'sum'
}).reset_index()

# Plot data
#plt.figure(figsize=(10,6))
#plt.plot(grouped_data['ga:date'], grouped_data['pageviews'], label='Pageviews', color='blue')
#plt.plot(grouped_data['ga:date'], grouped_data['users'], label='Users', color='orange')
# ... [previous code]

# Plot data with markers
plt.figure(figsize=(10,6))
plt.plot(grouped_data['ga:date'], grouped_data['pageviews'], label='Pageviews', color='blue', marker='o', markersize=5)
plt.plot(grouped_data['ga:date'], grouped_data['users'], label='Users', color='orange', marker='o', markersize=5)

# ... [rest of the code]


# Formatting the date on the x-axis for better readability
plt.gca().xaxis.set_major_formatter(plt.matplotlib.dates.DateFormatter('%Y-%m-%d'))
plt.xticks(rotation=45)  # Rotating the date for better visibility

plt.title('Google Analytics Data Visualization')
plt.xlabel('Date')
plt.ylabel('Count')
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.show()
