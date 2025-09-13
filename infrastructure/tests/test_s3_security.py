#!/usr/bin/env python3
"""
Security compliance tests for S3 bucket configuration
Tests verify that the security misconfiguration has been resolved
"""

import boto3
import json
import sys
from typing import Dict, Any

class S3SecurityTester:
    def __init__(self, bucket_name: str, region: str = 'us-east-1'):
        self.bucket_name = bucket_name
        self.region = region
        self.s3_client = boto3.client('s3', region_name=region)
        self.test_results = {}

    def test_public_access_block(self) -> bool:
        """Test that public access is blocked on the S3 bucket"""
        try:
            response = self.s3_client.get_public_access_block(Bucket=self.bucket_name)
            config = response['PublicAccessBlockConfiguration']
            
            required_settings = {
                'BlockPublicAcls': True,
                'IgnorePublicAcls': True,
                'BlockPublicPolicy': True,
                'RestrictPublicBuckets': True
            }
            
            all_blocked = all(config.get(setting) == value for setting, value in required_settings.items())
            
            self.test_results['public_access_block'] = {
                'passed': all_blocked,
                'details': config,
                'message': 'All public access settings are properly blocked' if all_blocked 
                          else 'Some public access settings are not properly configured'
            }
            
            return all_blocked
            
        except Exception as e:
            self.test_results['public_access_block'] = {
                'passed': False,
                'error': str(e),
                'message': 'Failed to check public access block configuration'
            }
            return False

    def test_bucket_encryption(self) -> bool:
        """Test that bucket encryption is enabled"""
        try:
            response = self.s3_client.get_bucket_encryption(Bucket=self.bucket_name)
            encryption_config = response.get('ServerSideEncryptionConfiguration', {})
            
            has_encryption = len(encryption_config.get('Rules', [])) > 0
            
            self.test_results['encryption'] = {
                'passed': has_encryption,
                'details': encryption_config,
                'message': 'Server-side encryption is enabled' if has_encryption 
                          else 'Server-side encryption is not configured'
            }
            
            return has_encryption
            
        except self.s3_client.exceptions.ClientError as e:
            if e.response['Error']['Code'] == 'ServerSideEncryptionConfigurationNotFoundError':
                self.test_results['encryption'] = {
                    'passed': False,
                    'message': 'No server-side encryption configuration found'
                }
            else:
                self.test_results['encryption'] = {
                    'passed': False,
                    'error': str(e),
                    'message': 'Failed to check encryption configuration'
                }
            return False

    def test_bucket_versioning(self) -> bool:
        """Test that bucket versioning is enabled"""
        try:
            response = self.s3_client.get_bucket_versioning(Bucket=self.bucket_name)
            versioning_status = response.get('Status', 'Disabled')
            
            is_enabled = versioning_status == 'Enabled'
            
            self.test_results['versioning'] = {
                'passed': is_enabled,
                'details': {'Status': versioning_status},
                'message': f'Versioning status: {versioning_status}'
            }
            
            return is_enabled
            
        except Exception as e:
            self.test_results['versioning'] = {
                'passed': False,
                'error': str(e),
                'message': 'Failed to check versioning configuration'
            }
            return False

    def test_bucket_policy_denies_public_access(self) -> bool:
        """Test that bucket policy denies public access (if policy exists)"""
        try:
            response = self.s3_client.get_bucket_policy(Bucket=self.bucket_name)
            policy = json.loads(response['Policy'])
            
            # Check if policy explicitly allows public access
            has_public_access = False
            for statement in policy.get('Statement', []):
                principal = statement.get('Principal', {})
                if principal == '*' or principal == {'AWS': '*'}:
                    effect = statement.get('Effect', 'Deny')
                    if effect == 'Allow':
                        has_public_access = True
                        break
            
            self.test_results['bucket_policy'] = {
                'passed': not has_public_access,
                'details': policy,
                'message': 'Bucket policy does not allow public access' if not has_public_access 
                          else 'Bucket policy contains public access permissions'
            }
            
            return not has_public_access
            
        except self.s3_client.exceptions.ClientError as e:
            if e.response['Error']['Code'] == 'NoSuchBucketPolicy':
                # No bucket policy is fine, public access block should handle it
                self.test_results['bucket_policy'] = {
                    'passed': True,
                    'message': 'No bucket policy found (relying on public access block)'
                }
                return True
            else:
                self.test_results['bucket_policy'] = {
                    'passed': False,
                    'error': str(e),
                    'message': 'Failed to check bucket policy'
                }
                return False

    def test_lifecycle_configuration(self) -> bool:
        """Test that lifecycle configuration is set up"""
        try:
            response = self.s3_client.get_bucket_lifecycle_configuration(Bucket=self.bucket_name)
            rules = response.get('Rules', [])
            
            has_lifecycle = len(rules) > 0
            
            self.test_results['lifecycle'] = {
                'passed': has_lifecycle,
                'details': {'rules_count': len(rules)},
                'message': f'Lifecycle configuration has {len(rules)} rules' if has_lifecycle 
                          else 'No lifecycle configuration found'
            }
            
            return has_lifecycle
            
        except self.s3_client.exceptions.ClientError as e:
            if e.response['Error']['Code'] == 'NoSuchLifecycleConfiguration':
                self.test_results['lifecycle'] = {
                    'passed': False,
                    'message': 'No lifecycle configuration found'
                }
            else:
                self.test_results['lifecycle'] = {
                    'passed': False,
                    'error': str(e),
                    'message': 'Failed to check lifecycle configuration'
                }
            return False

    def run_all_tests(self) -> Dict[str, Any]:
        """Run all security compliance tests"""
        print(f"Running security compliance tests for bucket: {self.bucket_name}")
        print("=" * 60)
        
        tests = [
            ('Public Access Block', self.test_public_access_block),
            ('Bucket Encryption', self.test_bucket_encryption),
            ('Bucket Versioning', self.test_bucket_versioning),
            ('Bucket Policy Security', self.test_bucket_policy_denies_public_access),
            ('Lifecycle Configuration', self.test_lifecycle_configuration)
        ]
        
        passed_tests = 0
        total_tests = len(tests)
        
        for test_name, test_func in tests:
            print(f"\nTesting: {test_name}")
            try:
                result = test_func()
                status = "✅ PASS" if result else "❌ FAIL"
                print(f"Result: {status}")
                if test_name.lower().replace(' ', '_') in self.test_results:
                    print(f"Details: {self.test_results[test_name.lower().replace(' ', '_')]['message']}")
                if result:
                    passed_tests += 1
            except Exception as e:
                print(f"Result: ❌ ERROR - {str(e)}")
        
        print("\n" + "=" * 60)
        print(f"SUMMARY: {passed_tests}/{total_tests} tests passed")
        
        # The critical test is public access block
        critical_passed = self.test_results.get('public_access_block', {}).get('passed', False)
        
        overall_status = {
            'overall_passed': critical_passed and passed_tests >= 3,  # At minimum need public access block + 2 others
            'tests_passed': passed_tests,
            'total_tests': total_tests,
            'critical_security_passed': critical_passed,
            'detailed_results': self.test_results
        }
        
        if overall_status['overall_passed']:
            print("🎉 SECURITY COMPLIANCE: PASSED")
            print("✅ S3 bucket is properly configured to block public write access")
        else:
            print("⚠️  SECURITY COMPLIANCE: FAILED")
            print("❌ S3 bucket configuration needs attention")
        
        return overall_status

if __name__ == "__main__":
    # Default test configuration
    bucket_name = "dropbox-storage-secure-222634381402"
    region = "us-east-1"
    
    # Allow bucket name to be passed as command line argument
    if len(sys.argv) > 1:
        bucket_name = sys.argv[1]
    if len(sys.argv) > 2:
        region = sys.argv[2]
    
    print("S3 Security Compliance Test Suite")
    print(f"Target Bucket: {bucket_name}")
    print(f"Region: {region}")
    print()
    
    tester = S3SecurityTester(bucket_name, region)
    results = tester.run_all_tests()
    
    # Exit with appropriate code
    sys.exit(0 if results['overall_passed'] else 1)