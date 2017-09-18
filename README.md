# ZImageManager
这是一个简单的图片加载器，精简修改自UniversalImageLoader开源库；

## 引用方式    
在Gradle文件的Dependencies下增加：  
	compile 'com.itz:ZImageLoader:1.0.5'   

## 使用示例    

	imageLoader.displayImage(new ImageViewAware(holder.image, imageUrls[position]), new ImageLoadingListener() {
		
                @Override
                public void onLoadingStarted(String imageUri, View view) {
                    // TODO Auto-generated method stub
                    System.out.println("Started");
                    holder.progressBar.setProgress(0);
                    holder.progressBar.setVisibility(View.VISIBLE);
                }

                @Override
                public void onLoadingFailed(String imageUri, View view,
                                            FailReason failReason) {
                    // TODO Auto-generated method stub
                    System.out.println("Failed");
                    holder.progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                    // TODO Auto-generated method stub
                    System.out.println("Complete");
                    holder.progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onLoadingCancelled(String imageUri, View view) {
                    // TODO Auto-generated method stub
                    System.out.println("Cancelled");
                    holder.progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onProgressUpdate(String imageUri, View view, int current,
                                             int total) {
                    // TODO Auto-generated method stub
                    holder.progressBar.setProgress(Math.round(100.0f * current / total));
                }
            });    
			

## 截图    
![image](https://github.com/ZhangSir/ZImageManager/blob/master/Screenshot_2017-09-15-17-52-37-716_com.itzs.imagemanager.png)