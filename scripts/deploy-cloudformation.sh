#!/bin/bash

# CloudFormation deployment script for DropBox S3 security configuration
# This script deploys secure S3 buckets with blocked public write access

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLOUDFORMATION_DIR="$SCRIPT_DIR/../cloudformation"
STACK_NAME="dropbox-s3-security"

echo "🔒 DropBox S3 Security Deployment Script (CloudFormation)"
echo "========================================================="

# Check prerequisites
echo "📋 Checking prerequisites..."

# Check if AWS CLI is installed and configured
if ! command -v aws &> /dev/null; then
    echo "❌ Error: AWS CLI is not installed. Please install AWS CLI"
    exit 1
fi

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    echo "❌ Error: AWS credentials not configured. Please run 'aws configure'"
    exit 1
fi

echo "✅ Prerequisites check passed"

# Navigate to CloudFormation directory
cd "$CLOUDFORMATION_DIR"

# Check if parameters.json exists
if [ ! -f "parameters.json" ]; then
    echo "⚠️  Warning: parameters.json not found"
    echo "📋 Please copy parameters.json.example to parameters.json and customize the values"
    echo ""
    echo "cp parameters.json.example parameters.json"
    echo ""
    read -p "Press Enter to continue after creating parameters.json, or Ctrl+C to exit..."
fi

# Validate parameters.json
if [ ! -f "parameters.json" ]; then
    echo "❌ Error: parameters.json still not found. Exiting."
    exit 1
fi

# Validate CloudFormation template
echo "🔧 Validating CloudFormation template..."
aws cloudformation validate-template --template-body file://s3-security.yaml > /dev/null

echo "📝 Preparing deployment..."

# Check if stack already exists
if aws cloudformation describe-stacks --stack-name "$STACK_NAME" &> /dev/null; then
    echo "⚠️  Stack '$STACK_NAME' already exists"
    read -p "Do you want to update the existing stack? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        ACTION="update-stack"
        echo "🔄 Updating existing stack..."
    else
        echo "❌ Deployment cancelled"
        exit 1
    fi
else
    ACTION="create-stack"
    echo "🆕 Creating new stack..."
fi

echo ""
echo "🚀 Ready to deploy secure S3 configuration"
echo "This will create:"
echo "  • S3 bucket with blocked public write access"
echo "  • Public access block configuration (all settings enabled)"
echo "  • Bucket encryption (AES256)"
echo "  • Versioning enabled"
echo "  • Lifecycle policies for cost optimization"
echo "  • Secure bucket policy"
echo ""

read -p "Do you want to proceed with the deployment? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🔨 Deploying CloudFormation stack..."
    
    if [ "$ACTION" = "create-stack" ]; then
        aws cloudformation create-stack \
            --stack-name "$STACK_NAME" \
            --template-body file://s3-security.yaml \
            --parameters file://parameters.json \
            --capabilities CAPABILITY_IAM \
            --tags Key=Purpose,Value="S3 Security Remediation" Key=Project,Value="DropBox"
    else
        aws cloudformation update-stack \
            --stack-name "$STACK_NAME" \
            --template-body file://s3-security.yaml \
            --parameters file://parameters.json \
            --capabilities CAPABILITY_IAM
    fi
    
    echo "⏳ Waiting for stack deployment to complete..."
    aws cloudformation wait stack-$ACTION-complete --stack-name "$STACK_NAME"
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ Deployment completed successfully!"
        echo "📊 Stack outputs:"
        aws cloudformation describe-stacks \
            --stack-name "$STACK_NAME" \
            --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
            --output table
        
        echo ""
        echo "🔍 Security Verification:"
        echo "Run the following command to verify public access is blocked:"
        bucket_name=$(aws cloudformation describe-stacks \
            --stack-name "$STACK_NAME" \
            --query 'Stacks[0].Outputs[?OutputKey==`BucketName`].OutputValue' \
            --output text)
        echo "aws s3api get-public-access-block --bucket $bucket_name"
    else
        echo "❌ Deployment failed. Check CloudFormation console for details."
        exit 1
    fi
else
    echo "❌ Deployment cancelled"
    exit 1
fi

echo ""
echo "🎉 S3 security configuration deployed successfully!"
echo "📖 See docs/AWS_S3_SECURITY.md for validation steps and troubleshooting"