for dir in */
do
	echo $dir
    ( cd $dir && bash includeFile gen.md>README.md )
done