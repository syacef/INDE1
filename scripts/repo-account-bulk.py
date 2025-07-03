#!/usr/bin/env python3
import csv
import json
import random
import string
import time
import requests

FIRST_NAMES = [
    "John", "Jane", "Michael", "Sarah", "David", "Emma", "Robert", "Lisa",
    "James", "Mary", "William", "Jennifer", "Richard", "Patricia", "Charles",
    "Linda", "Thomas", "Elizabeth", "Christopher", "Barbara", "Daniel", "Susan",
    "Matthew", "Jessica", "Anthony", "Karen", "Mark", "Nancy", "Donald", "Betty",
    "Paul", "Helen", "Steven", "Sharon", "Kenneth", "Michelle", "Joshua", "Laura",
    "Kevin", "Sarah", "Brian", "Kimberly", "George", "Deborah", "Edward", "Dorothy"
]

LAST_NAMES = [
    "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
    "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
    "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
    "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker",
    "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Adams"
]

def generate_license_plate(letters1, number, letters2):
    return f"{letters1}-{number:03d}-{letters2}"

def generate_random_user(parking_plate, handicapped_probability=0.05):
    first_name = random.choice(FIRST_NAMES)
    last_name = random.choice(LAST_NAMES)
    username = f"{first_name.lower()}{last_name.lower()}{random.randint(1, 999)}"
    email = f"{first_name.lower()}.{last_name.lower()}@example.com"
    handicapped = random.random() < handicapped_probability
    created_at = random.randint(1640995200000, 1719964800000)
    
    return {
        "parkingPlate": parking_plate,
        "username": username,
        "email": email,
        "firstName": first_name,
        "lastName": last_name,
        "createdAt": created_at,
        "handicapped": handicapped
    }

def generate_bulk_users(
    letter_combinations=None,
    number_range=(0, 999),
    handicapped_probability=0.05,
    output_format="csv",
    output_file="users_with_plates",
    limit=None
):
    """
    Generate bulk users with license plates
    
    Args:
        letter_combinations: List of tuples for letter combinations, e.g., [("AA", "AA"), ("AB", "CD")]
                           If None, generates all combinations from A-Z
        number_range: Tuple of (min, max) for number range
        handicapped_probability: Probability of user being handicapped (0.0-1.0)
        output_format: "csv" or "json"
        output_file: Output filename (without extension)
        limit: Maximum number of users to generate (None for all)
    """
    
    users = []
    count = 0
    
    # Default to AA-AA if no combinations specified
    if letter_combinations is None:
        letter_combinations = [("AA", "AA")]
    
    print(f"Generating users with handicapped probability: {handicapped_probability*100}%")
    print(f"Number range: {number_range[0]:03d} to {number_range[1]:03d}")
    print(f"Letter combinations: {letter_combinations}")
    
    for letters1, letters2 in letter_combinations:
        for number in range(number_range[0], number_range[1] + 1):
            if limit and count >= limit:
                break
                
            plate = generate_license_plate(letters1, number, letters2)
            user = generate_random_user(plate, handicapped_probability)
            users.append(user)
            count += 1
            
            if count % 1000 == 0:
                print(f"Generated {count} users...")
        
        if limit and count >= limit:
            break
    
    # Output results
    if output_format.lower() == "json":
        filename = f"{output_file}.json"
        with open(filename, 'w') as f:
            json.dump(users, f, indent=2)
    else:  # CSV
        filename = f"{output_file}.csv"
        with open(filename, 'w', newline='') as f:
            if users:
                writer = csv.DictWriter(f, fieldnames=users[0].keys())
                writer.writeheader()
                writer.writerows(users)
    
    handicapped_count = sum(1 for user in users if user['handicapped'])
    print(f"\nGenerated {len(users)} users")
    print(f"Handicapped users: {handicapped_count} ({handicapped_count/len(users)*100:.1f}%)")
    print(f"Output saved to: {filename}")
    
    return users

def send_users_to_endpoint(users, endpoint_url, batch_size=100, headers=None):    
    if headers is None:
        headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        }
    
    total_users = len(users)
    successful_batches = 0
    failed_batches = 0
    
    print(f"Sending {total_users} users to {endpoint_url}")
    print(f"Batch size: {batch_size}")
    
    for i in range(0, total_users, batch_size):
        batch = users[i:i + batch_size]
        batch_num = (i // batch_size) + 1
        total_batches = (total_users + batch_size - 1) // batch_size
        
        try:
            print(f"Sending batch {batch_num}/{total_batches} ({len(batch)} users)...")
            
            response = requests.post(
                endpoint_url,
                json=batch,
                headers=headers,
                timeout=30
            )
            
            if response.status_code == 201:
                successful_batches += 1
                print(f"  ✓ Batch {batch_num} successful")
            else:
                failed_batches += 1
                print(f"  ✗ Batch {batch_num} failed: {response.status_code} - {response.text}")
                
        except requests.exceptions.RequestException as e:
            failed_batches += 1
            print(f"  ✗ Batch {batch_num} failed with error: {str(e)}")
        
        # Small delay between requests to avoid overwhelming the server
        if i + batch_size < total_users:
            time.sleep(0.1)
    
    print(f"\nSummary:")
    print(f"  Total batches: {total_batches}")
    print(f"  Successful: {successful_batches}")
    print(f"  Failed: {failed_batches}")
    print(f"  Success rate: {successful_batches/total_batches*100:.1f}%")
    
    return successful_batches, failed_batches

def generate_all_letter_combinations():
    letters = string.ascii_uppercase
    return [(l1+l2, l3+l4) for l1 in letters for l2 in letters 
            for l3 in letters for l4 in letters]

def generate_and_send_users(
    endpoint_url,
    letter_combinations=None,
    number_range=(0, 999),
    handicapped_probability=0.05,
    batch_size=100,
    limit=None,
    headers=None,
    save_to_file=True
):
    users = []
    count = 0
    
    if letter_combinations is None:
        letter_combinations = [("AA", "AA")]
    
    print(f"Generating and sending users to: {endpoint_url}")
    print(f"Handicapped probability: {handicapped_probability*100}%")
    print(f"Batch size: {batch_size}")
    
    for letters1, letters2 in letter_combinations:
        for number in range(number_range[0], number_range[1] + 1):
            if limit and count >= limit:
                break
                
            plate = generate_license_plate(letters1, number, letters2)
            user = generate_random_user(plate, handicapped_probability)
            users.append(user)
            count += 1
            
            if len(users) >= batch_size:
                send_users_to_endpoint([users[-batch_size:]], endpoint_url, batch_size, headers)
                
            if count % 1000 == 0:
                print(f"Generated {count} users...")
        
        if limit and count >= limit:
            break
    
    if users:
        remaining = len(users) % batch_size
        if remaining > 0:
            send_users_to_endpoint(users[-remaining:], endpoint_url, remaining, headers)
    
    if save_to_file:
        filename = "generated_users.json"
        with open(filename, 'w') as f:
            json.dump(users, f, indent=2)
        print(f"Users also saved to: {filename}")
    
    return users

if __name__ == "__main__":
    print("Bulk User Generator with License Plates")
    print("=" * 50)

    ENDPOINT_URL = "http://localhost:8080/account/bulk"
    print(f"\nSending to endpoint: {ENDPOINT_URL}")

    generate_and_send_users(
        endpoint_url=ENDPOINT_URL,
        letter_combinations=[("AA", "AA")],
        number_range=(0, 99),
        handicapped_probability=0.08,
        batch_size=50,
        headers={'Content-Type': 'application/json'}
    )

    print("\nDone!")
